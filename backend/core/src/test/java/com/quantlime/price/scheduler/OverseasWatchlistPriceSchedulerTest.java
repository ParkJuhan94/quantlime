package com.quantlime.price.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.lock.PriceRelayLeaderGate;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossPriceResponse;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.price.cache.OverseasMarketCalendarCache;
import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.cache.WatchlistedStockCodeCache;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.realtime.PriceTopicSubscriptionTracker;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OverseasWatchlistPriceSchedulerTest {

    private static final String STOCK_CODE = "AAPL";

    @Mock
    private OverseasMarketCalendarCache overseasMarketCalendarCache;

    @Mock
    private WatchlistedStockCodeCache overseasWatchlistedStockCodeCache;

    @Mock
    private PreviousCloseCache overseasPreviousCloseCache;

    @Mock
    private MarketRankingCache overseasMarketRankingCache;

    @Mock
    private TossApiClient tossApiClient;

    @Mock
    private PriceCacheStore priceCacheStore;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PriceRelayLeaderGate priceRelayLeaderGate;

    @Mock
    private PriceTopicSubscriptionTracker priceTopicSubscriptionTracker;

    @InjectMocks
    private OverseasWatchlistPriceScheduler overseasWatchlistPriceScheduler;

    // DomesticWatchlistPriceRelaySchedulerTest와 동일 이유 - 이 클래스의
    // 시나리오는 "리더일 때" 이후 분기를 검증하는 목적.
    @BeforeEach
    void setUpLeader() {
        given(priceRelayLeaderGate.isLeader()).willReturn(true);
        // 기본은 모든 종목에 구독자가 있는 상태 - 구독자 필터는 별도 테스트가 검증한다.
        lenient().when(priceTopicSubscriptionTracker.hasSubscribers(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("[리더가 아니면 아무것도 조회·발행하지 않고 스킵한다]")
    void refresh_notLeader_skipsEntirely() {
        // given
        given(priceRelayLeaderGate.isLeader()).willReturn(false);

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then
        verify(overseasMarketCalendarCache, never()).isMarketOpenNow();
        verify(overseasWatchlistedStockCodeCache, never()).get();
    }

    @Test
    @DisplayName("[미국장이 닫혀 있으면 관심종목 조회도 하지 않고 스킵한다]")
    void refresh_marketClosed_skipsEntirely() {
        // given
        given(overseasMarketCalendarCache.isMarketOpenNow()).willReturn(false);

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then
        verify(overseasWatchlistedStockCodeCache, never()).get();
        verify(tossApiClient, never()).getCurrentPrices(anyString());
    }

    @Test
    @DisplayName("[해외 관심종목이 없으면 Toss를 호출하지 않는다]")
    void refresh_emptyWatchlist_skipsTossCall() {
        // given
        given(overseasMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of());

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then
        verify(tossApiClient, never()).getCurrentPrices(anyString());
    }

    @Test
    @DisplayName("[구독자가 없는 종목은 캐시에는 저장하되 브로드캐스트는 하지 않는다]")
    void refresh_noSubscribers_savesButDoesNotBroadcast() {
        // given
        given(overseasMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(overseasPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 340.0));
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(
            new TossPriceResponse(List.of(
                new TossPriceResponse.TossPrice(STOCK_CODE, "2026-07-29T17:43:12+09:00", "341.43", "USD"))));
        given(priceTopicSubscriptionTracker.hasSubscribers(STOCK_CODE)).willReturn(false);

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then
        verify(priceCacheStore).saveAll(anyList());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("[Toss 현재가 조회 성공 시 전일종가 대비 등락률을 계산해 캐시에 저장하고 브로드캐스트한다]")
    void refresh_success_savesAndBroadcastsWithChangeRate() {
        // given: 340 -> 341.43은 약 +0.42%
        given(overseasMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(overseasPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 340.0));
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(
            new TossPriceResponse(List.of(
                new TossPriceResponse.TossPrice(STOCK_CODE, "2026-07-29T17:43:12+09:00", "341.43", "USD"))));

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then: 종목당 개별 save 대신 청크 단위 saveAll(파이프라인)로 바뀌었다
        // (2026-08-19, PriceCacheStore.saveAll 참고).
        ArgumentCaptor<List<PriceSnapshot>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(priceCacheStore).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).hasSize(1);
        PriceSnapshot saved = savedCaptor.getValue().get(0);
        assertThat(saved.stockCode()).isEqualTo(STOCK_CODE);
        assertThat(saved.currentPrice()).isCloseTo(341.43, offset(0.001));
        assertThat(saved.changeRate()).isCloseTo(0.4206, offset(0.001));
        verify(messagingTemplate).convertAndSend(eq("/topic/price." + STOCK_CODE), eq(saved));
    }

    @Test
    @DisplayName("[전일종가가 없으면 등락률 null로 저장하되 현재가는 그대로 캐시한다]")
    void refresh_noPreviousClose_savesWithNullChangeRate() {
        // given
        given(overseasMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(overseasPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of());
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(
            new TossPriceResponse(List.of(
                new TossPriceResponse.TossPrice(STOCK_CODE, "2026-07-29T17:43:12+09:00", "341.43", "USD"))));

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then
        ArgumentCaptor<List<PriceSnapshot>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(priceCacheStore).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).hasSize(1);
        assertThat(savedCaptor.getValue().get(0).changeRate()).isNull();
    }

    @Test
    @DisplayName("[Toss 조회가 실패해도 예외가 전파되지 않는다(다음 틱에 재시도)]")
    void refresh_tossCallFails_doesNotPropagate() {
        // given
        given(overseasMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(overseasPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 340.0));
        given(tossApiClient.getCurrentPrices(STOCK_CODE))
            .willThrow(new ExternalApiException(TossApiErrorCode.RATE_LIMIT_EXCEEDED));

        // when & then
        assertThatCode(() -> overseasWatchlistPriceScheduler.refreshAndBroadcast())
            .doesNotThrowAnyException();
        verify(priceCacheStore, never()).saveAll(any());
    }

    @Test
    @DisplayName("[등락률을 계산할 수 있고 로컬 stock 테이블에 있는 종목만 랭킹 캐시에 쓴다]")
    void refresh_success_updatesRankingCacheWithChangeRateAndStockMeta() {
        // given: 340 -> 341.43은 약 +0.42%
        given(overseasMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(overseasPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 340.0));
        given(stockRepository.findByStockCodeIn(List.of(STOCK_CODE)))
            .willReturn(List.of(overseasStock(STOCK_CODE, "Apple")));
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(
            new TossPriceResponse(List.of(
                new TossPriceResponse.TossPrice(STOCK_CODE, "2026-07-29T17:43:12+09:00", "341.43", "USD"))));

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then
        ArgumentCaptor<List<MarketRankingResponse>> rankingCaptor = ArgumentCaptor.forClass(List.class);
        verify(overseasMarketRankingCache).update(rankingCaptor.capture());
        assertThat(rankingCaptor.getValue()).hasSize(1);
        MarketRankingResponse ranked = rankingCaptor.getValue().get(0);
        assertThat(ranked.stockCode()).isEqualTo(STOCK_CODE);
        assertThat(ranked.changeRate()).isCloseTo(0.4206, offset(0.001));
        assertThat(ranked.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("[전일종가가 없어 등락률을 계산할 수 없는 종목은 랭킹 캐시에서 제외한다]")
    void refresh_noPreviousClose_excludesFromRankingCache() {
        // given
        given(overseasMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(overseasPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of());
        given(stockRepository.findByStockCodeIn(List.of(STOCK_CODE)))
            .willReturn(List.of(overseasStock(STOCK_CODE, "Apple")));
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(
            new TossPriceResponse(List.of(
                new TossPriceResponse.TossPrice(STOCK_CODE, "2026-07-29T17:43:12+09:00", "341.43", "USD"))));

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then
        verify(overseasMarketRankingCache).update(List.of());
    }

    @Test
    @DisplayName("[로컬 stock 테이블에 없는 종목은 등락률이 있어도 랭킹 캐시에서 제외한다]")
    void refresh_stockNotInLocalTable_excludesFromRankingCache() {
        // given
        given(overseasMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(overseasPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 340.0));
        given(stockRepository.findByStockCodeIn(List.of(STOCK_CODE))).willReturn(List.of());
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(
            new TossPriceResponse(List.of(
                new TossPriceResponse.TossPrice(STOCK_CODE, "2026-07-29T17:43:12+09:00", "341.43", "USD"))));

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then: 시세 자체는 여전히 캐시·브로드캐스트되지만(위 다른 테스트 참고) 랭킹만 빠진다
        verify(overseasMarketRankingCache).update(List.of());
    }

    private Stock overseasStock(String stockCode, String stockName) {
        return Stock.of(stockCode, stockName, MarketType.NASDAQ, ListingStatus.LISTED, null);
    }
}
