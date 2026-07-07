package com.quantlab.price.scheduler;

import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.infra.toss.dto.TossPriceResponse.TossPrice;
import com.quantlab.price.cache.MarketCalendarCache;
import com.quantlab.price.cache.PreviousCloseCache;
import com.quantlab.price.cache.PriceCacheStore;
import com.quantlab.price.cache.WatchlistedStockCodeCache;
import com.quantlab.price.dto.response.PriceBroadcastMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PriceBroadcastSchedulerTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private MarketCalendarCache marketCalendarCache;

    @Mock
    private WatchlistedStockCodeCache watchlistedStockCodeCache;

    @Mock
    private PreviousCloseCache previousCloseCache;

    @Mock
    private PriceCacheStore priceCacheStore;

    @Mock
    private TossApiClient tossApiClient;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PriceBroadcastScheduler priceBroadcastScheduler;

    @Test
    @DisplayName("[장이 닫혀 있으면 관심종목 조회도 하지 않고 스킵한다]")
    void broadcast_marketClosed_skipsEntirely() {
        // given
        given(marketCalendarCache.isMarketOpenNow()).willReturn(false);

        // when
        priceBroadcastScheduler.broadcastCurrentPrices();

        // then
        verify(watchlistedStockCodeCache, never()).get();
        verify(tossApiClient, never()).getCurrentPrices(anyString());
    }

    @Test
    @DisplayName("[관심 종목이 없으면 Toss를 호출하지 않는다]")
    void broadcast_emptyWatchlist_skipsTossCall() {
        // given
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(watchlistedStockCodeCache.get()).willReturn(List.of());

        // when
        priceBroadcastScheduler.broadcastCurrentPrices();

        // then
        verify(tossApiClient, never()).getCurrentPrices(anyString());
    }

    @Test
    @DisplayName("[정상 틱이면 등락률을 계산해 캐시에 적재하고 토픽으로 브로드캐스트한다]")
    void broadcast_normalTick_cachesAndBroadcastsWithChangeRate() {
        // given
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(watchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(previousCloseCache.get(List.of(STOCK_CODE)))
            .willReturn(Map.of(STOCK_CODE, 70000L));
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(new TossPriceResponse(
            List.of(new TossPrice(STOCK_CODE, "2026-07-06T09:00:00+09:00", "71400", "KRW"))));

        // when
        priceBroadcastScheduler.broadcastCurrentPrices();

        // then: (71400-70000)/70000*100 = 2.0
        ArgumentCaptor<PriceBroadcastMessage> messageCaptor =
            ArgumentCaptor.forClass(PriceBroadcastMessage.class);
        verify(priceCacheStore).save(messageCaptor.capture());
        PriceBroadcastMessage message = messageCaptor.getValue();
        assertThat(message.stockCode()).isEqualTo(STOCK_CODE);
        assertThat(message.currentPrice()).isEqualTo(71400L);
        assertThat(message.changeRate()).isEqualTo(2.0);

        verify(messagingTemplate).convertAndSend("/topic/price/" + STOCK_CODE, message);
    }

    @Test
    @DisplayName("[전일 종가가 없으면 등락률은 null로 두고도 브로드캐스트한다]")
    void broadcast_noPreviousClose_broadcastsWithNullChangeRate() {
        // given
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(watchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(previousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of());
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(new TossPriceResponse(
            List.of(new TossPrice(STOCK_CODE, "2026-07-06T09:00:00+09:00", "71400", "KRW"))));

        // when
        priceBroadcastScheduler.broadcastCurrentPrices();

        // then
        ArgumentCaptor<PriceBroadcastMessage> messageCaptor =
            ArgumentCaptor.forClass(PriceBroadcastMessage.class);
        verify(priceCacheStore).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().changeRate()).isNull();
    }

    @Test
    @DisplayName("[현재가가 빈 문자열이면 해당 종목은 캐싱·브로드캐스트하지 않는다]")
    void broadcast_blankLastPrice_skipsThatStock() {
        // given
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(watchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(previousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 70000L));
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(new TossPriceResponse(
            List.of(new TossPrice(STOCK_CODE, "2026-07-06T09:00:00+09:00", "", "KRW"))));

        // when
        priceBroadcastScheduler.broadcastCurrentPrices();

        // then
        verify(priceCacheStore, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("[Toss 호출이 실패해도 예외가 전파되지 않는다(다음 틱에 영향 없음)]")
    void broadcast_tossFails_doesNotPropagate() {
        // given
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(watchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(previousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of());
        given(tossApiClient.getCurrentPrices(STOCK_CODE))
            .willThrow(new RuntimeException("토스 API 장애"));

        // when & then: SafeExecutor가 내부에서 흡수하므로 예외가 밖으로 나오면 안 됨
        assertThatCode(() -> priceBroadcastScheduler.broadcastCurrentPrices())
            .doesNotThrowAnyException();
    }
}
