package com.quantlime.market.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

import com.quantlime.market.service.MarketDataRefreshService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketDataStartupRunnerTest {

    @Mock
    private MarketDataRefreshService marketDataRefreshService;

    @Mock
    private TaskExecutor marketDataStartupTaskExecutor;

    @InjectMocks
    private MarketDataStartupRunner marketDataStartupRunner;

    @Test
    @DisplayName("[기동 시 전종목 갱신을 전용 실행기에 제출한다(기동 스레드를 직접 막지 않음)]")
    void run_submitsRefreshAllToDedicatedExecutor() {
        // given: 실행기가 즉시 동기 실행하는 것처럼 스텁(제출 자체만 검증하면 충분)
        willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).given(marketDataStartupTaskExecutor).execute(any());

        // when
        marketDataStartupRunner.run(null);

        // then
        verify(marketDataRefreshService).refreshAll();
    }
}
