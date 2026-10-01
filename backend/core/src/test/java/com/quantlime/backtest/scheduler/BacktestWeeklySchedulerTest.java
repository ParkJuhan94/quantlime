package com.quantlime.backtest.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.repository.BacktestDailyScoreRepository;
import com.quantlime.backtest.service.BacktestDatasetPreparationService;
import com.quantlime.backtest.service.BacktestUniverseService;
import com.quantlime.backtest.service.CrossSectionalBacktestService;
import com.quantlime.common.lock.RedisLockService;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BacktestWeeklySchedulerTest {

    @Mock
    private RedisLockService redisLockService;

    @Mock
    private BacktestDatasetPreparationService datasetPreparationService;

    @Mock
    private BacktestUniverseService universeService;

    @Mock
    private CrossSectionalBacktestService crossSectionalBacktestService;

    @Mock
    private BacktestDailyScoreRepository backtestDailyScoreRepository;

    @InjectMocks
    private BacktestWeeklyScheduler scheduler;

    @SuppressWarnings("unchecked")
    private void lockAcquired() {
        given(redisLockService.runExclusively(eq("lock:backtest-weekly"), eq(Duration.ofHours(3)), any(Supplier.class)))
            .willAnswer(invocation -> Optional.of(((Supplier<Boolean>) invocation.getArgument(2)).get()));
    }

    @Test
    @DisplayName("[락을 잡으면 데이터셋 준비 → 유니버스 백테스트 → 최신 scoreVersion으로 횡단면 백테스트를 순서대로 실행한다]")
    void runWeeklyBacktest_runsThreeStepsInOrder() {
        // given
        lockAcquired();
        BacktestDailyScore latest = mock(BacktestDailyScore.class);
        given(latest.getScoreVersion()).willReturn("v3.0");
        given(backtestDailyScoreRepository.findTopByOrderByIdDesc()).willReturn(Optional.of(latest));

        // when
        scheduler.runWeeklyBacktest();

        // then
        InOrder order = inOrder(datasetPreparationService, universeService, crossSectionalBacktestService);
        order.verify(datasetPreparationService).prepareDataset();
        order.verify(universeService).runUniverse(false);
        order.verify(crossSectionalBacktestService).runAllMarkets("v3.0", false, 200);
    }

    @Test
    @DisplayName("[backtest_daily_score가 비어 있으면 횡단면 백테스트만 건너뛴다]")
    void runWeeklyBacktest_noScoreVersion_skipsCrossSectional() {
        // given
        lockAcquired();
        given(backtestDailyScoreRepository.findTopByOrderByIdDesc()).willReturn(Optional.empty());

        // when
        scheduler.runWeeklyBacktest();

        // then
        verify(datasetPreparationService).prepareDataset();
        verify(universeService).runUniverse(false);
        verify(crossSectionalBacktestService, never()).runAllMarkets(anyString(), anyBoolean(), anyInt());
    }

    @Test
    @DisplayName("[다른 실행(관리자 수동 트리거 등)이 락을 쥐고 있으면 아무 단계도 실행하지 않는다]")
    @SuppressWarnings("unchecked")
    void runWeeklyBacktest_lockHeld_skipsAll() {
        given(redisLockService.runExclusively(eq("lock:backtest-weekly"), any(Duration.class), any(Supplier.class)))
            .willReturn(Optional.empty());

        scheduler.runWeeklyBacktest();

        verifyNoInteractions(datasetPreparationService, universeService, crossSectionalBacktestService);
    }

    @Test
    @DisplayName("[중간 단계가 실패하면 예외를 전파하지 않고 이후 단계는 실행하지 않는다]")
    void runWeeklyBacktest_stepFails_stopsAndDoesNotPropagate() {
        // given
        lockAcquired();
        willThrow(new RuntimeException("db")).given(datasetPreparationService).prepareDataset();

        // when & then
        assertThatCode(() -> scheduler.runWeeklyBacktest()).doesNotThrowAnyException();
        verifyNoInteractions(universeService, crossSectionalBacktestService);
    }
}
