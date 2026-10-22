package com.quantlime.batch.backtest;

import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.dto.response.UniverseSelection;
import com.quantlime.backtest.implement.BacktestReader;
import com.quantlime.backtest.service.BacktestDatasetPreparationService;
import com.quantlime.backtest.service.BacktestService;
import com.quantlime.backtest.service.BacktestUniverseService;
import com.quantlime.backtest.service.CrossSectionalBacktestService;
import com.quantlime.batch.common.BatchFailureNotifier;
import com.quantlime.stock.domain.MarketType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 주간 백테스트 Job들. Step을 한 번 정의해 Job 4개가 재사용한다.
 *
 * <ul>
 *   <li>{@value #WEEKLY_JOB}: 데이터셋 준비 → 유니버스 백테스트 → 횡단면 백테스트 (주간 스케줄러)
 *   <li>{@value #DATASET_JOB} / {@value #UNIVERSE_JOB} / {@value #CROSS_SECTIONAL_JOB}: 관리자 수동 트리거용 부분 Job
 * </ul>
 *
 * <p>연산은 Spring이 아니라 Python quant-engine이 한다. 병렬도({@code backtest-batch.parallelism})는
 * 기본 1(순차, 기존과 동일)이고, 올려도 엔진 워커(uvicorn {@code WEB_CONCURRENCY})와 컨테이너 메모리가 따라 주지 않으면
 * 빨라지지 않는다. 운영은 t3.medium(vCPU 2, 4GB)이라 실측 없이 올리지 않는다.
 *
 * <p>파티셔닝용 실행기는 빈으로 등록하지 않고 Step 설정 안에서만 만든다 - Executor 빈이 있으면 Boot의
 * {@code applicationTaskExecutor} 자동 구성이 물러나 다른 곳의 동작이 바뀐다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BacktestJobConfig {

    public static final String WEEKLY_JOB = "backtestWeeklyJob";
    public static final String DATASET_JOB = "backtestDatasetJob";
    public static final String UNIVERSE_JOB = "backtestUniverseJob";
    public static final String CROSS_SECTIONAL_JOB = "crossSectionalBacktestJob";

    public static final String PARAM_FORCE = "force";
    public static final String PARAM_MARKET = "market";
    public static final String PARAM_SCORE_VERSION = "scoreVersion";
    public static final String PARAM_NULL_TEST = "nullTest";
    public static final String PARAM_NULL_REPEATS = "nullRepeats";

    static final String CTX_UNIVERSE = "backtest.universe";
    // 4개 고정 시장 - CrossSectionalBacktestService의 벤치마크 매핑과 같은 집합이다. Map.of 순회는 순서가 불안정해 명시한다
    private static final List<MarketType> CROSS_SECTIONAL_MARKETS = List.of(
        MarketType.KOSPI, MarketType.KOSDAQ, MarketType.NASDAQ, MarketType.NYSE);
    private static final int DEFAULT_NULL_REPEATS = 200;
    // 유니버스 종목 중 이력 부족(INSUFFICIENT_HISTORY) 같은 종목별 실패가 정상적으로 섞여 있어 넉넉히 둔다. 파티션 단위 상한이다
    private static final int UNIVERSE_SKIP_LIMIT = 200;

    private final JobRepository jobRepository;
    private final BacktestDatasetPreparationService datasetPreparationService;
    private final BacktestService backtestService;
    private final BacktestUniverseService universeService;
    private final CrossSectionalBacktestService crossSectionalService;
    private final BacktestReader backtestReader;
    private final BatchFailureNotifier failureNotifier;

    @Value("${backtest-batch.parallelism:1}")
    private int parallelism;

    @Bean
    public Job backtestWeeklyJob(Step backtestDatasetStep, Step backtestUniverseStep, Step crossSectionalStep) {
        return new JobBuilder(WEEKLY_JOB, jobRepository).listener(failureNotifier)
            .start(backtestDatasetStep).next(backtestUniverseStep).next(crossSectionalStep).build();
    }

    @Bean
    public Job backtestDatasetJob(Step backtestDatasetStep) {
        return new JobBuilder(DATASET_JOB, jobRepository).listener(failureNotifier).start(backtestDatasetStep).build();
    }

    @Bean
    public Job backtestUniverseJob(Step backtestDatasetStep, Step backtestUniverseStep) {
        return new JobBuilder(UNIVERSE_JOB, jobRepository).listener(failureNotifier).start(backtestDatasetStep).next(backtestUniverseStep).build();
    }

    @Bean
    public Job crossSectionalBacktestJob(Step crossSectionalStep) {
        return new JobBuilder(CROSS_SECTIONAL_JOB, jobRepository).listener(failureNotifier).start(crossSectionalStep).build();
    }

    // ---- 1) 데이터셋 준비: 유니버스를 JobExecutionContext에 남겨 restart 때 같은 유니버스를 쓴다 ----

    @Bean
    public Step backtestDatasetStep(PlatformTransactionManager transactionManager) {
        Tasklet tasklet = (contribution, chunkContext) -> {
            UniverseSelection selection = datasetPreparationService.prepareDataset();
            ExecutionContext jobContext = chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext();
            // 목록 객체를 그대로 넣으면 컨텍스트 직렬화(Jackson)가 불변 컬렉션을 못 읽는 경우가 있어 문자열 하나로 둔다
            jobContext.putString(CTX_UNIVERSE, String.join(",", selection.all()));
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("backtestDatasetStep", jobRepository).tasklet(tasklet, transactionManager).build();
    }

    // ---- 2) 유니버스 백테스트: 종목 구간으로 파티셔닝, 종목 1건이 커밋(=재시작) 단위 ----

    @Bean
    public Step backtestUniverseStep(Partitioner backtestUniversePartitioner, Step backtestUniverseWorkerStep) {
        return new StepBuilder("backtestUniverseStep", jobRepository)
            .partitioner("backtestUniverseWorkerStep", backtestUniversePartitioner)
            .step(backtestUniverseWorkerStep)
            .gridSize(Math.max(1, parallelism))
            .taskExecutor(partitionExecutor("backtest-universe-"))
            .build();
    }

    @Bean
    @StepScope
    public Partitioner backtestUniversePartitioner(
            @Value("#{jobExecutionContext['" + CTX_UNIVERSE + "']}") @Nullable String universeCsv) {
        return gridSize -> {
            int total = splitUniverse(universeCsv).size();
            int size = Math.max(1, gridSize);
            int perPartition = (total + size - 1) / size;
            Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
            for (int i = 0; i < size && i * perPartition < total; i++) {
                ExecutionContext context = new ExecutionContext();
                context.putInt("start", i * perPartition);
                context.putInt("end", Math.min(total, (i + 1) * perPartition));
                partitions.put("universe" + i, context);
            }
            log.info("유니버스 백테스트 파티션 구성: 종목={}, 파티션={}", total, partitions.size());
            return partitions;
        };
    }

    @Bean
    public Step backtestUniverseWorkerStep(RangeStockCodeReader backtestUniverseReader,
                                           ItemProcessor<String, String> backtestUniverseProcessor) {
        ItemWriter<String> writer = chunk -> chunk.getItems().forEach(backtestService::runBacktest);
        return new StepBuilder("backtestUniverseWorkerStep", jobRepository)
            .<String, String>chunk(1, new ResourcelessTransactionManager())
            .reader(backtestUniverseReader)
            .processor(backtestUniverseProcessor)
            .writer(writer)
            .faultTolerant()
            .skip(Exception.class)
            // 엔진이 죽은 상태에서 종목을 전부 skip하면 빈 결과로 "성공"하므로 서킷 OPEN은 Step을 실패시켜 restart를 기다린다
            .noSkip(CallNotPermittedException.class)
            .skipLimit(UNIVERSE_SKIP_LIMIT)
            .listener(new BacktestSkipListener("backtestUniverseWorkerStep"))
            .build();
    }

    @Bean
    @StepScope
    public RangeStockCodeReader backtestUniverseReader(
            @Value("#{jobExecutionContext['" + CTX_UNIVERSE + "']}") @Nullable String universeCsv,
            @Value("#{stepExecutionContext['start']}") Integer start,
            @Value("#{stepExecutionContext['end']}") Integer end) {
        return new RangeStockCodeReader("backtestUniverseReader", splitUniverse(universeCsv), start, end);
    }

    @Bean
    @StepScope
    public ItemProcessor<String, String> backtestUniverseProcessor(
            @Value("#{jobParameters['" + PARAM_FORCE + "']}") @Nullable String force) {
        boolean forceAll = Boolean.parseBoolean(force);
        // null을 돌려주면 Batch가 항목을 걸러낸다(filterCount) - 오늘 이미 돈 종목을 건너뛰는 기존 규칙과 같다
        return stockCode -> !forceAll && universeService.isAlreadyBacktestedToday(stockCode) ? null : stockCode;
    }

    // ---- 3) 횡단면 백테스트: 시장 4개가 서로 독립이라 시장 단위로 파티셔닝 ----

    @Bean
    public Step crossSectionalStep(Partitioner crossSectionalPartitioner, Step crossSectionalWorkerStep) {
        return new StepBuilder("crossSectionalStep", jobRepository)
            .partitioner("crossSectionalWorkerStep", crossSectionalPartitioner)
            .step(crossSectionalWorkerStep)
            // 파티션 수는 시장 수로 고정하고 실제 동시 실행은 실행기의 동시성 제한(=parallelism)으로 조절한다
            .gridSize(CROSS_SECTIONAL_MARKETS.size())
            .taskExecutor(partitionExecutor("backtest-cross-"))
            .build();
    }

    @Bean
    @StepScope
    public Partitioner crossSectionalPartitioner(
            @Value("#{jobParameters['" + PARAM_MARKET + "']}") @Nullable String market) {
        return gridSize -> {
            Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
            for (MarketType candidate : CROSS_SECTIONAL_MARKETS) {
                if (market == null || market.isBlank() || candidate.name().equals(market)) {
                    ExecutionContext context = new ExecutionContext();
                    context.putString("market", candidate.name());
                    partitions.put("market-" + candidate.name(), context);
                }
            }
            return partitions;
        };
    }

    @Bean
    public Step crossSectionalWorkerStep(Tasklet crossSectionalTasklet, PlatformTransactionManager transactionManager) {
        return new StepBuilder("crossSectionalWorkerStep", jobRepository)
            .tasklet(crossSectionalTasklet, transactionManager).build();
    }

    @Bean
    @StepScope
    public Tasklet crossSectionalTasklet(
            @Value("#{stepExecutionContext['market']}") String market,
            @Value("#{jobParameters['" + PARAM_SCORE_VERSION + "']}") @Nullable String scoreVersionParam,
            @Value("#{jobParameters['" + PARAM_NULL_TEST + "']}") @Nullable String nullTestParam,
            @Value("#{jobParameters['" + PARAM_NULL_REPEATS + "']}") @Nullable String nullRepeatsParam) {
        return (contribution, chunkContext) -> {
            // 주간 Job은 scoreVersion을 주지 않는다 - 유니버스 Step이 저장한 최신 버전을 이 시점에 읽는다
            Optional<String> scoreVersion = scoreVersionParam != null && !scoreVersionParam.isBlank()
                ? Optional.of(scoreVersionParam)
                : backtestReader.findLatestDailyScore().map(BacktestDailyScore::getScoreVersion);
            if (scoreVersion.isEmpty()) {
                log.warn("횡단면 백테스트 건너뜀: backtest_daily_score가 비어 있음, market={}", market);
                return RepeatStatus.FINISHED;
            }
            int nullRepeats = nullRepeatsParam == null ? DEFAULT_NULL_REPEATS : Integer.parseInt(nullRepeatsParam);
            crossSectionalService.runForMarket(MarketType.valueOf(market), scoreVersion.get(),
                Boolean.parseBoolean(nullTestParam), nullRepeats);
            return RepeatStatus.FINISHED;
        };
    }

    // ---- 공통 ----

    private SimpleAsyncTaskExecutor partitionExecutor(String threadNamePrefix) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(threadNamePrefix);
        executor.setConcurrencyLimit(Math.max(1, parallelism));
        return executor;
    }

    private static List<String> splitUniverse(@Nullable String universeCsv) {
        if (universeCsv == null || universeCsv.isBlank()) {
            return List.of();
        }
        return new ArrayList<>(Arrays.asList(universeCsv.split(",")));
    }
}
