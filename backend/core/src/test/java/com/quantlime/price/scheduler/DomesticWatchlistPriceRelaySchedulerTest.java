package com.quantlime.price.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.common.lock.PriceRelayLeaderGate;
import com.quantlime.price.cache.DomesticMarketCalendarCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.cache.WatchlistedStockCodeCache;
import com.quantlime.price.dto.response.PriceSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DomesticWatchlistPriceRelaySchedulerTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private DomesticMarketCalendarCache domesticMarketCalendarCache;

    @Mock
    private WatchlistedStockCodeCache domesticWatchlistedStockCodeCache;

    @Mock
    private PriceCacheStore priceCacheStore;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PriceRelayLeaderGate priceRelayLeaderGate;

    @InjectMocks
    private DomesticWatchlistPriceRelayScheduler domesticWatchlistPriceRelayScheduler;

    // 이 클래스의 모든 시나리오는 "리더일 때" 이후의 분기를 검증하는
    // 목적이라(비-리더 스킵은 별도 테스트로 검증) 공통 기본값으로 둔다.
    @BeforeEach
    void setUpLeader() {
        given(priceRelayLeaderGate.isLeader()).willReturn(true);
    }

    @Test
    @DisplayName("[리더가 아니면 아무것도 조회·발행하지 않고 스킵한다]")
    void broadcast_notLeader_skipsEntirely() {
        // given
        given(priceRelayLeaderGate.isLeader()).willReturn(false);

        // when
        domesticWatchlistPriceRelayScheduler.broadcastCurrentPrices();

        // then
        verify(domesticMarketCalendarCache, never()).isMarketOpenNow();
        verify(domesticWatchlistedStockCodeCache, never()).get();
    }

    @Test
    @DisplayName("[장이 닫혀 있으면 관심종목 조회도 하지 않고 스킵한다]")
    void broadcast_marketClosed_skipsEntirely() {
        // given
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(false);

        // when
        domesticWatchlistPriceRelayScheduler.broadcastCurrentPrices();

        // then
        verify(domesticWatchlistedStockCodeCache, never()).get();
        verify(priceCacheStore, never()).findAll(anyList());
    }

    @Test
    @DisplayName("[관심 종목이 없으면 캐시 조회도 하지 않는다]")
    void broadcast_emptyWatchlist_skipsCacheLookup() {
        // given
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(domesticWatchlistedStockCodeCache.get()).willReturn(List.of());

        // when
        domesticWatchlistPriceRelayScheduler.broadcastCurrentPrices();

        // then
        verify(priceCacheStore, never()).findAll(anyList());
    }

    @Test
    @DisplayName("[Redis에 캐시된 시세가 있으면 그대로 토픽으로 브로드캐스트한다(Toss를 직접 호출하지 않음)]")
    void broadcast_cacheHit_broadcastsWithoutCallingToss() {
        // given: 관심종목 수만큼 순차 find 대신 findAll 파이프라인 하나로
        // 조회한다(2026-08-19).
        PriceSnapshot cached = new PriceSnapshot(STOCK_CODE, 71400.0, 2.0, "2026-07-15T09:00:00+09:00");
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(domesticWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(priceCacheStore.findAll(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, cached));

        // when
        domesticWatchlistPriceRelayScheduler.broadcastCurrentPrices();

        // then
        verify(messagingTemplate).convertAndSend("/topic/price." + STOCK_CODE, cached);
    }

    @Test
    @DisplayName("[Redis에 아직 시세가 없으면 그 종목은 브로드캐스트하지 않는다]")
    void broadcast_cacheMiss_skipsThatStock() {
        // given
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(domesticWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(priceCacheStore.findAll(List.of(STOCK_CODE))).willReturn(Map.of());

        // when
        domesticWatchlistPriceRelayScheduler.broadcastCurrentPrices();

        // then
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("[캐시 조회가 실패해도 예외가 전파되지 않는다(다음 틱에 영향 없음)]")
    void broadcast_cacheLookupFails_doesNotPropagate() {
        // given
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(domesticWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(priceCacheStore.findAll(List.of(STOCK_CODE))).willThrow(new RuntimeException("Redis 장애"));

        // when & then: SafeExecutor가 내부에서 흡수하므로 예외가 밖으로 나오면 안 됨
        assertThatCode(() -> domesticWatchlistPriceRelayScheduler.broadcastCurrentPrices())
            .doesNotThrowAnyException();
    }
}
