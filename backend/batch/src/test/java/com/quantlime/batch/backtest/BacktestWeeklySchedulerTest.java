package com.quantlime.batch.backtest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.quantlime.batch.common.BatchLaunchRejectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BacktestWeeklySchedulerTest {

    @Mock
    private BacktestBatchLauncher launcher;

    @InjectMocks
    private BacktestWeeklyScheduler scheduler;

    @Test
    @DisplayName("[주간 스케줄은 주간 Job을 시작만 한다]")
    void runWeeklyBacktest_launchesWeeklyJob() {
        given(launcher.launchWeekly()).willReturn(21L);

        scheduler.runWeeklyBacktest();

        verify(launcher).launchWeekly();
    }

    @Test
    @DisplayName("[다른 백테스트 Job이 실행 중이라 거부되어도 예외를 던지지 않는다]")
    void runWeeklyBacktest_rejected_doesNotThrow() {
        given(launcher.launchWeekly()).willThrow(new BatchLaunchRejectedException("이미 실행 중인 배치가 있습니다"));

        assertThatCode(scheduler::runWeeklyBacktest).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[시작 중 예상 밖 예외가 나도 스케줄러 스레드로 전파하지 않는다]")
    void runWeeklyBacktest_unexpectedFailure_doesNotThrow() {
        given(launcher.launchWeekly()).willThrow(new IllegalStateException("boom"));

        assertThatCode(scheduler::runWeeklyBacktest).doesNotThrowAnyException();
    }
}
