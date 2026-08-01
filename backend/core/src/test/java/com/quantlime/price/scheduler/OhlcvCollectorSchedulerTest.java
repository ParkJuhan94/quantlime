package com.quantlime.price.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.quantlime.market.service.MarketDataRefreshService;
import java.util.Optional;
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
    @DisplayName("[매일 16:00 배치는 전종목 가격/스코어 갱신을 트리거1(MarketDataRefreshService)에 "
        + "락을 잡은 채로(refreshAllExclusively) 위임한다]")
    void collectDailyOhlcv_delegatesToMarketDataRefreshServiceExclusively() {
        // given
        given(marketDataRefreshService.refreshAllExclusively()).willReturn(Optional.of(Boolean.TRUE));

        // when
        ohlcvCollectorScheduler.collectDailyOhlcv();

        // then
        verify(marketDataRefreshService).refreshAllExclusively();
    }

    @Test
    @DisplayName("[이미 다른 실행(기동 캐치업 등)이 갱신 중이면 조용히 스킵한다 - 예외 없이 정상 종료]")
    void collectDailyOhlcv_lockHeld_skipsWithoutException() {
        // given
        given(marketDataRefreshService.refreshAllExclusively()).willReturn(Optional.empty());

        // when & then: 예외 없이 정상 종료돼야 한다
        ohlcvCollectorScheduler.collectDailyOhlcv();
    }

    @Test
    @DisplayName("[갱신이 실패해도 예외가 전파되지 않는다(try-catch로 격리)]")
    void collectDailyOhlcv_refreshFails_doesNotPropagate() {
        // given
        willThrow(new RuntimeException("boom")).given(marketDataRefreshService).refreshAllExclusively();

        // when & then: 예외 없이 정상 종료돼야 한다
        ohlcvCollectorScheduler.collectDailyOhlcv();
    }
}
