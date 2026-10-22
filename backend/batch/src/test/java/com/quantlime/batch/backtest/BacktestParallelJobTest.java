package com.quantlime.batch.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.backtest.dto.response.UniverseSelection;
import com.quantlime.batch.BatchIntegrationTestSupport;
import com.quantlime.batch.common.BatchExecutionView;
import com.quantlime.batch.common.BatchLaunchRejectedException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** 병렬도 3에서 파티셔닝: 종목 정확히 한 번 처리, 실패한 파티션만 restart, 백테스트 Job 간 상호 배제. */
@TestPropertySource(properties = "backtest-batch.parallelism=3")
class BacktestParallelJobTest extends BatchIntegrationTestSupport {

    private static final List<String> SIX = List.of("S0", "S1", "S2", "S3", "S4", "S5");

    @Autowired
    private BacktestBatchLauncher launcher;

    @Test
    @DisplayName("[병렬도 3이면 6종목이 3개 파티션으로 나뉘고 모든 종목이 정확히 한 번씩 처리된다]")
    void partitions_processEachStockOnce() {
        given(datasetPreparationService.prepareDataset()).willReturn(new UniverseSelection(SIX, List.of()));

        BatchExecutionView view = runToEnd(launcher.launchUniverse(false));

        assertThat(view.status()).isEqualTo("COMPLETED");
        SIX.forEach(code -> verify(backtestService, times(1)).runBacktest(code));
        long partitionSteps = view.steps().stream()
            .filter(step -> step.name().startsWith("backtestUniverseWorkerStep:universe")).count();
        assertThat(partitionSteps).isEqualTo(3);
    }

    @Test
    @DisplayName("[한 파티션이 서킷 OPEN으로 실패하면 FAILED, restart는 그 파티션만 다시 돌고 유니버스는 다시 선정하지 않는다]")
    void restart_rerunsOnlyFailedPartition() {
        given(datasetPreparationService.prepareDataset()).willReturn(new UniverseSelection(SIX, List.of()));
        CallNotPermittedException open = CallNotPermittedException.createCallNotPermittedException(
            CircuitBreaker.ofDefaults("quant-engine-test"));
        AtomicInteger s2Calls = new AtomicInteger();
        // 두 번째 파티션의 첫 종목(S2)이 처음 한 번만 서킷 OPEN으로 실패한다
        doAnswer(invocation -> {
            if (s2Calls.incrementAndGet() == 1) {
                throw open;
            }
            return null;
        }).when(backtestService).runBacktest("S2");

        BatchExecutionView failed = runToEnd(launcher.launchUniverse(false));
        assertThat(failed.status()).isEqualTo("FAILED");

        BatchExecutionView restarted = runToEnd(launchService.restart(failed.executionId()));

        assertThat(restarted.status()).isEqualTo("COMPLETED");
        // 완료된 파티션(S0,S1 / S4,S5)은 다시 호출되지 않고, 실패한 파티션의 S2는 두 번, S3는 한 번이다
        List.of("S0", "S1", "S3", "S4", "S5").forEach(code -> verify(backtestService, times(1)).runBacktest(code));
        verify(backtestService, times(2)).runBacktest("S2");
        verify(datasetPreparationService, times(1)).prepareDataset();
    }

    @Test
    @DisplayName("[백테스트 Job이 실행 중이면 이름이 다른 백테스트 Job도 거부하지만 스코어 재구성 Job은 시작할 수 있다]")
    void exclusion_acrossBacktestJobs_notScoreRebuild() throws Exception {
        given(datasetPreparationService.prepareDataset()).willReturn(new UniverseSelection(List.of("S0"), List.of()));
        given(stockMasterService.getAllListedStocks()).willReturn(List.of());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            release.await(30, TimeUnit.SECONDS);
            return null;
        }).when(backtestService).runBacktest(anyString());

        long running = launcher.launchUniverse(false);
        try {
            assertThat(entered.await(30, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(launcher::launchWeekly).isInstanceOf(BatchLaunchRejectedException.class);
            assertThatThrownBy(() -> launcher.launchCrossSectional(null, "v3.0", false, 200))
                .isInstanceOf(BatchLaunchRejectedException.class);

            BatchExecutionView scoreRebuild = runToEnd(launchService.launchScoreRebuild(LocalDate.of(2026, 10, 1)));
            assertThat(scoreRebuild.status()).isEqualTo("COMPLETED");
        } finally {
            release.countDown();
        }
        assertThat(runToEnd(running).status()).isEqualTo("COMPLETED");
        verify(crossSectionalService, never()).runForMarket(any(), anyString(), anyBoolean(), anyInt());
    }
}
