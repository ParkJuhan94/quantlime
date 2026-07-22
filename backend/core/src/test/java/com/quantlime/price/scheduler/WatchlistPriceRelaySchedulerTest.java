package com.quantlime.price.scheduler;

import com.quantlime.price.cache.MarketCalendarCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.cache.WatchlistedStockCodeCache;
import com.quantlime.price.dto.response.PriceSnapshot;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class WatchlistPriceRelaySchedulerTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private MarketCalendarCache marketCalendarCache;

    @Mock
    private WatchlistedStockCodeCache watchlistedStockCodeCache;

    @Mock
    private PriceCacheStore priceCacheStore;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WatchlistPriceRelayScheduler watchlistPriceRelayScheduler;

    @Test
    @DisplayName("[장이 닫혀 있으면 관심종목 조회도 하지 않고 스킵한다]")
    void broadcast_marketClosed_skipsEntirely() {
        // given
        given(marketCalendarCache.isMarketOpenNow()).willReturn(false);

        // when
        watchlistPriceRelayScheduler.broadcastCurrentPrices();

        // then
        verify(watchlistedStockCodeCache, never()).get();
        verify(priceCacheStore, never()).find(anyString());
    }

    @Test
    @DisplayName("[관심 종목이 없으면 캐시 조회도 하지 않는다]")
    void broadcast_emptyWatchlist_skipsCacheLookup() {
        // given
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(watchlistedStockCodeCache.get()).willReturn(List.of());

        // when
        watchlistPriceRelayScheduler.broadcastCurrentPrices();

        // then
        verify(priceCacheStore, never()).find(anyString());
    }

    @Test
    @DisplayName("[Redis에 캐시된 시세가 있으면 그대로 토픽으로 브로드캐스트한다(Toss를 직접 호출하지 않음)]")
    void broadcast_cacheHit_broadcastsWithoutCallingToss() {
        // given
        PriceSnapshot cached = new PriceSnapshot(STOCK_CODE, 71400L, 2.0, "2026-07-15T09:00:00+09:00");
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(watchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(priceCacheStore.find(STOCK_CODE)).willReturn(Optional.of(cached));

        // when
        watchlistPriceRelayScheduler.broadcastCurrentPrices();

        // then
        verify(messagingTemplate).convertAndSend("/topic/price/" + STOCK_CODE, cached);
    }

    @Test
    @DisplayName("[Redis에 아직 시세가 없으면 그 종목은 브로드캐스트하지 않는다]")
    void broadcast_cacheMiss_skipsThatStock() {
        // given
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(watchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(priceCacheStore.find(STOCK_CODE)).willReturn(Optional.empty());

        // when
        watchlistPriceRelayScheduler.broadcastCurrentPrices();

        // then
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("[캐시 조회가 실패해도 예외가 전파되지 않는다(다음 틱에 영향 없음)]")
    void broadcast_cacheLookupFails_doesNotPropagate() {
        // given
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(watchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(priceCacheStore.find(STOCK_CODE)).willThrow(new RuntimeException("Redis 장애"));

        // when & then: SafeExecutor가 내부에서 흡수하므로 예외가 밖으로 나오면 안 됨
        assertThatCode(() -> watchlistPriceRelayScheduler.broadcastCurrentPrices())
            .doesNotThrowAnyException();
    }
}
