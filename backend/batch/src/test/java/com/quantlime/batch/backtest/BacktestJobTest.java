package com.quantlime.batch.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.dto.response.UniverseSelection;
import com.quantlime.batch.BatchIntegrationTestSupport;
import com.quantlime.batch.common.BatchExecutionView;
import com.quantlime.stock.domain.MarketType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;

/** 기본 병렬도(1)에서 백테스트 Job 4개의 Step 순서, 유니버스 필터·skip, 횡단면 시장 선택을 검증한다. */
class BacktestJobTest extends BatchIntegrationTestSupport {

    @Autowired
    private BacktestBatchLauncher launcher;

    private void stubUniverse(List<String> domestic, List<String> overseas) {
        given(datasetPreparationService.prepareDataset()).willReturn(new UniverseSelection(domestic, overseas));
    }

    private void stubLatestScoreVersion(String version) {
        BacktestDailyScore latest = mock(BacktestDailyScore.class);
        given(latest.getScoreVersion()).willReturn(version);
        given(backtestReader.findLatestDailyScore()).willReturn(Optional.of(latest));
    }

    @Test
    @DisplayName("[주간 Job: 데이터셋 준비 → 유니버스 종목별 백테스트 → 4개 시장 횡단면 백테스트를 이 순서로 실행한다]")
    void weeklyJob_runsThreeStepsInOrder() {
        stubUniverse(List.of("D1", "D2"), List.of("O1"));
        stubLatestScoreVersion("v3.0");

        BatchExecutionView view = runToEnd(launcher.launchWeekly());

        assertThat(view.status()).isEqualTo("COMPLETED");
        InOrder order = inOrder(datasetPreparationService, backtestService, crossSectionalService);
        order.verify(datasetPreparationService).prepareDataset();
        order.verify(backtestService).runBacktest("D1");
        order.verify(backtestService).runBacktest("D2");
        order.verify(backtestService).runBacktest("O1");
        order.verify(crossSectionalService, times(4)).runForMarket(any(MarketType.class), eq("v3.0"), eq(false), eq(200));
        verify(crossSectionalService).runForMarket(MarketType.KOSPI, "v3.0", false, 200);
        verify(crossSectionalService).runForMarket(MarketType.KOSDAQ, "v3.0", false, 200);
        verify(crossSectionalService).runForMarket(MarketType.NASDAQ, "v3.0", false, 200);
        verify(crossSectionalService).runForMarket(MarketType.NYSE, "v3.0", false, 200);
    }

    @Test
    @DisplayName("[backtest_daily_score가 비어 있으면 횡단면만 건너뛰고 Job은 COMPLETED다]")
    void weeklyJob_noScoreVersion_skipsCrossSectionalOnly() {
        stubUniverse(List.of("D1"), List.of());
        given(backtestReader.findLatestDailyScore()).willReturn(Optional.empty());

        BatchExecutionView view = runToEnd(launcher.launchWeekly());

        assertThat(view.status()).isEqualTo("COMPLETED");
        verify(backtestService).runBacktest("D1");
        verify(crossSectionalService, never()).runForMarket(any(), anyString(), anyBoolean(), anyInt());
    }

    @Test
    @DisplayName("[유니버스 Job: 오늘 이미 돈 종목은 걸러내고 force=true면 전부 다시 실행한다]")
    void universeJob_filtersAlreadyBacktested_unlessForce() {
        stubUniverse(List.of("D1", "D2"), List.of("O1"));
        given(universeService.isAlreadyBacktestedToday("D2")).willReturn(true);

        BatchExecutionView normal = runToEnd(launcher.launchUniverse(false));
        verify(backtestService).runBacktest("D1");
        verify(backtestService, never()).runBacktest("D2");
        verify(backtestService).runBacktest("O1");
        assertThat(normal.status()).isEqualTo("COMPLETED");

        BatchExecutionView forced = runToEnd(launcher.launchUniverse(true));
        assertThat(forced.status()).isEqualTo("COMPLETED");
        verify(backtestService).runBacktest("D2");
        // 필터 대상이 아니었던 종목은 두 번 실행됐다
        verify(backtestService, times(2)).runBacktest("D1");
    }

    @Test
    @DisplayName("[종목 하나의 예외는 그 종목만 skip하고 나머지는 계속한다]")
    void universeJob_failingStock_isSkipped() {
        stubUniverse(List.of("D1", "D2", "D3"), List.of());
        doThrow(new IllegalStateException("이력 부족")).when(backtestService).runBacktest("D2");

        BatchExecutionView view = runToEnd(launcher.launchUniverse(false));

        assertThat(view.status()).isEqualTo("COMPLETED");
        verify(backtestService).runBacktest("D1");
        verify(backtestService).runBacktest("D3");
        long skipped = view.steps().stream()
            .filter(step -> step.name().startsWith("backtestUniverseWorkerStep:")).mapToLong(step -> step.skipCount()).sum();
        assertThat(skipped).isEqualTo(1);
    }

    @Test
    @DisplayName("[횡단면 Job: market을 주면 그 시장만, scoreVersion·nullTest·nullRepeats를 그대로 전달한다]")
    void crossSectionalJob_singleMarket() {
        BatchExecutionView view = runToEnd(launcher.launchCrossSectional("KOSPI", "v3.0", true, 50));

        assertThat(view.status()).isEqualTo("COMPLETED");
        verify(crossSectionalService).runForMarket(MarketType.KOSPI, "v3.0", true, 50);
        verify(crossSectionalService, times(1)).runForMarket(any(), anyString(), anyBoolean(), anyInt());
    }

    @Test
    @DisplayName("[데이터셋 Job은 데이터셋 준비만 실행한다]")
    void datasetJob_runsOnlyPreparation() {
        stubUniverse(List.of("D1"), List.of());

        BatchExecutionView view = runToEnd(launcher.launchDataset());

        assertThat(view.status()).isEqualTo("COMPLETED");
        verify(datasetPreparationService).prepareDataset();
        verify(backtestService, never()).runBacktest(anyString());
    }
}
