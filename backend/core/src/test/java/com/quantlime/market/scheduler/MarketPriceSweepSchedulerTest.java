package com.quantlime.market.scheduler;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossPriceResponse;
import com.quantlime.infra.toss.dto.TossPriceResponse.TossPrice;
import com.quantlime.market.cache.AllListedStockCache;
import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.price.cache.MarketCalendarCache;
import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketPriceSweepSchedulerTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private MarketCalendarCache marketCalendarCache;

    @Mock
    private AllListedStockCache allListedStockCache;

    @Mock
    private PreviousCloseCache previousCloseCache;

    @Mock
    private MarketRankingCache marketRankingCache;

    @Mock
    private TossApiClient tossApiClient;

    @Mock
    private PriceCacheStore priceCacheStore;

    // 실제 MeterRegistry 구현체로 넣어야 timer()/counter() 호출이 null을
    // 반환하지 않는다(순수 Mockito mock은 값을 반환하지 않아 NPE 위험).
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private MarketPriceSweepScheduler marketPriceSweepScheduler;

    @Test
    @DisplayName("[장이 닫혀 있으면 종목 목록 조회도 하지 않고 스킵한다]")
    void refresh_marketClosed_skipsEntirely() {
        // given
        given(marketCalendarCache.isMarketOpenNow()).willReturn(false);

        // when
        marketPriceSweepScheduler.refreshRanking();

        // then
        verify(allListedStockCache, never()).get();
        verify(marketRankingCache, never()).update(anyList());
    }

    @Test
    @DisplayName("[상장 종목이 없으면 Toss를 호출하지 않는다]")
    void refresh_noListedStocks_skipsTossCall() {
        // given
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(allListedStockCache.get()).willReturn(List.of());

        // when
        marketPriceSweepScheduler.refreshRanking();

        // then
        verify(tossApiClient, never()).getCurrentPrices(anyString());
    }

    @Test
    @DisplayName("[정상 틱이면 등락률을 계산해 랭킹 캐시를 갱신하고 시세를 Redis에 적재한다]")
    void refresh_normalTick_updatesRankingCacheAndPriceCache() {
        // given
        Stock stock = StockFixture.createStock(STOCK_CODE, "삼성전자");
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(allListedStockCache.get()).willReturn(List.of(stock));
        given(previousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 70000L));
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(new TossPriceResponse(
            List.of(new TossPrice(STOCK_CODE, "2026-07-06T09:00:00+09:00", "71400", "KRW"))));

        // when
        marketPriceSweepScheduler.refreshRanking();

        // then: (71400-70000)/70000*100 = 2.0
        ArgumentCaptor<List<MarketRankingResponse>> captor = ArgumentCaptor.forClass(List.class);
        verify(marketRankingCache).update(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        MarketRankingResponse ranked = captor.getValue().get(0);
        assertThat(ranked.stockCode()).isEqualTo(STOCK_CODE);
        assertThat(ranked.stockName()).isEqualTo("삼성전자");
        assertThat(ranked.currentPrice()).isEqualTo(71400L);
        assertThat(ranked.changeRate()).isEqualTo(2.0);

        ArgumentCaptor<PriceSnapshot> cacheCaptor = ArgumentCaptor.forClass(PriceSnapshot.class);
        verify(priceCacheStore).save(cacheCaptor.capture());
        assertThat(cacheCaptor.getValue().stockCode()).isEqualTo(STOCK_CODE);
        assertThat(cacheCaptor.getValue().currentPrice()).isEqualTo(71400L);
        assertThat(cacheCaptor.getValue().changeRate()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("[전일 종가가 없는 종목은 랭킹에서는 제외하지만 시세 캐시에는 그대로 적재한다]")
    void refresh_noPreviousClose_excludesFromRankingButStillCachesPrice() {
        // given
        Stock stock = StockFixture.createStock(STOCK_CODE, "삼성전자");
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(allListedStockCache.get()).willReturn(List.of(stock));
        given(previousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of());
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(new TossPriceResponse(
            List.of(new TossPrice(STOCK_CODE, "2026-07-06T09:00:00+09:00", "71400", "KRW"))));

        // when
        marketPriceSweepScheduler.refreshRanking();

        // then
        ArgumentCaptor<List<MarketRankingResponse>> captor = ArgumentCaptor.forClass(List.class);
        verify(marketRankingCache).update(captor.capture());
        assertThat(captor.getValue()).isEmpty();

        ArgumentCaptor<PriceSnapshot> cacheCaptor = ArgumentCaptor.forClass(PriceSnapshot.class);
        verify(priceCacheStore).save(cacheCaptor.capture());
        assertThat(cacheCaptor.getValue().currentPrice()).isEqualTo(71400L);
        assertThat(cacheCaptor.getValue().changeRate()).isNull();
    }

    @Test
    @DisplayName("[Toss 호출이 실패해도 예외가 전파되지 않는다(다음 틱에 영향 없음)]")
    void refresh_tossFails_doesNotPropagate() {
        // given
        Stock stock = StockFixture.createStock(STOCK_CODE, "삼성전자");
        given(marketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(allListedStockCache.get()).willReturn(List.of(stock));
        given(previousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 70000L));
        given(tossApiClient.getCurrentPrices(STOCK_CODE))
            .willThrow(new RuntimeException("토스 API 장애"));

        // when & then: SafeExecutor가 내부에서 흡수하므로 예외가 밖으로 나오면 안 됨
        assertThatCode(() -> marketPriceSweepScheduler.refreshRanking())
            .doesNotThrowAnyException();
    }
}
