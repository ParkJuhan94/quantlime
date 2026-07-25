package com.quantlime.price.scheduler;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.quantlime.market.service.MarketDataRefreshService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OhlcvCollectorSchedulerTest {

    @Mock
    private MarketDataRefreshService marketDataRefreshService;

    @InjectMocks
    private OhlcvCollectorScheduler ohlcvCollectorScheduler;

    @Test
    @DisplayName("[매일 16:00 배치는 전종목 가격/스코어 갱신을 트리거1(MarketDataRefreshService)에 위임한다]")
    void collectDailyOhlcv_delegatesToMarketDataRefreshService() {
        // when
        ohlcvCollectorScheduler.collectDailyOhlcv();

        // then
        verify(marketDataRefreshService).refreshAll();
    }

    @Test
    @DisplayName("[갱신이 실패해도 예외가 전파되지 않는다(SafeExecutor로 격리)]")
    void collectDailyOhlcv_refreshFails_doesNotPropagate() {
        // given
        willThrow(new RuntimeException("boom")).given(marketDataRefreshService).refreshAll();

        // when & then: 예외 없이 정상 종료돼야 한다
        ohlcvCollectorScheduler.collectDailyOhlcv();
    }
}
