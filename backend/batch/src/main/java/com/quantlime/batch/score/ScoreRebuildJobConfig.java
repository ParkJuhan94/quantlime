package com.quantlime.batch.score;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.score.implement.ScoreAppender;
import com.quantlime.score.service.ScoreService;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 스코어 재구성 Job. {@code ScoreService#rebuildScoresFrom}이 "삭제 → 전종목 재계산"을 한 호출로 묶어 두어
 * 중간에 끊기면 스코어가 지워진 채 남던 문제를 Step 단위 재시작으로 해결한다.
 *
 * <ol>
 *   <li>{@code scoreRebuildDeleteStep}: from 이후 스코어 삭제. restart 때 이미 COMPLETED면 건너뛰므로 삭제는 한 번만 일어난다.
 *   <li>{@code domesticScoreRecalcStep} / {@code overseasScoreRecalcStep}: 100종목씩 재계산.
 * </ol>
 *
 * <p>재계산 Step의 트랜잭션 매니저는 {@link ResourcelessTransactionManager}다 - quant-engine HTTP 호출을 트랜잭션 밖에서
 * 수행한다는 ScoreService의 원칙을 지키려는 것이고, 저장은 {@code ScoreAppender#saveAll}이 자체 트랜잭션으로 한다.
 * 같은 종목을 다시 써도 upsert라 중복이 생기지 않는다. 이 매니저를 빈으로 등록하면 JPA 트랜잭션 매니저의
 * 자동 구성이 물러나므로 Step 안에서만 직접 만든다.
 *
 * <p>실패 정책: quant-engine 호출 실패({@link ExternalApiException})는 2회까지 시도하고, 그래도 실패하면 청크를
 * 종목 단위로 다시 써 보며 실패 종목만 skip한다(상한 50). 서킷 OPEN({@link CallNotPermittedException})은 엔진이 죽은
 * 상태라 skip하면 전종목이 비므로 Step을 실패시키고 restart를 기다린다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ScoreRebuildJobConfig {

    public static final String JOB_NAME = "scoreRebuildJob";
    public static final String PARAM_FROM = "from";

    private static final int CHUNK_SIZE = 100;
    private static final int RETRY_LIMIT = 2;
    private static final int SKIP_LIMIT = 50;

    private final JobRepository jobRepository;
    private final StockMasterService stockMasterService;
    private final ScoreService scoreService;
    private final ScoreAppender scoreAppender;

    @Bean
    public Job scoreRebuildJob(Step scoreRebuildDeleteStep, Step domesticScoreRecalcStep,
                               Step overseasScoreRecalcStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .start(scoreRebuildDeleteStep)
            .next(domesticScoreRecalcStep)
            .next(overseasScoreRecalcStep)
            .build();
    }

    @Bean
    public Step scoreRebuildDeleteStep(Tasklet scoreRebuildDeleteTasklet,
                                       PlatformTransactionManager transactionManager) {
        return new StepBuilder("scoreRebuildDeleteStep", jobRepository)
            .tasklet(scoreRebuildDeleteTasklet, transactionManager)
            .build();
    }

    @Bean
    @StepScope
    public Tasklet scoreRebuildDeleteTasklet(@Value("#{jobParameters['" + PARAM_FROM + "']}") String from) {
        return (contribution, chunkContext) -> {
            LocalDate fromDate = LocalDate.parse(from);
            log.info("스코어 재구성 시작 - 삭제 단계: from={}", fromDate);
            scoreAppender.deleteFrom(fromDate);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step domesticScoreRecalcStep() {
        return recalcStep("domesticScoreRecalcStep", stock -> stock.getMarketType().isDomestic(),
            codes -> scoreService.recalculateDomesticScoresChunk(codes));
    }

    @Bean
    public Step overseasScoreRecalcStep() {
        return recalcStep("overseasScoreRecalcStep", stock -> !stock.getMarketType().isDomestic(),
            codes -> scoreService.recalculateOverseasScoresChunk(codes));
    }

    private Step recalcStep(String stepName, Predicate<Stock> marketFilter, ChunkRecalculator recalculator) {
        ListedStockCodeReader reader = new ListedStockCodeReader(stepName + "Reader",
            () -> stockMasterService.getAllListedStocks().stream()
                .filter(marketFilter)
                .map(Stock::getStockCode)
                .toList());
        ItemWriter<String> writer = chunk -> recalculator.recalculate(List.copyOf(chunk.getItems()));

        return new StepBuilder(stepName, jobRepository)
            .<String, String>chunk(CHUNK_SIZE, new ResourcelessTransactionManager())
            .reader(reader)
            .writer(writer)
            .faultTolerant()
            .retry(ExternalApiException.class)
            .retryLimit(RETRY_LIMIT)
            .skip(Exception.class)
            .noSkip(CallNotPermittedException.class)
            .skipLimit(SKIP_LIMIT)
            .listener(new ScoreRebuildSkipListener(stepName))
            .build();
    }

    @FunctionalInterface
    private interface ChunkRecalculator {
        void recalculate(List<String> stockCodes);
    }
}
