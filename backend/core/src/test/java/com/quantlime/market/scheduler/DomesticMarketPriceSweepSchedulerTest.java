package com.quantlime.market.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.lock.PriceRelayLeaderGate;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossPriceResponse;
import com.quantlime.infra.toss.dto.TossPriceResponse.TossPrice;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import com.quantlime.market.cache.DomesticListedStockCache;
import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.price.cache.DomesticMarketCalendarCache;
import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DomesticMarketPriceSweepSchedulerTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private DomesticMarketCalendarCache domesticMarketCalendarCache;

    @Mock
    private DomesticListedStockCache domesticListedStockCache;

    @Mock
    private PreviousCloseCache domesticPreviousCloseCache;

    @Mock
    private MarketRankingCache domesticMarketRankingCache;

    @Mock
    private TossApiClient tossApiClient;

    @Mock
    private PriceCacheStore priceCacheStore;

    // 실제 MeterRegistry 구현체로 넣어야 timer()/counter() 호출이 null을
    // 반환하지 않는다(순수 Mockito mock은 값을 반환하지 않아 NPE 위험).
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private PriceRelayLeaderGate priceRelayLeaderGate;

    @InjectMocks
    private DomesticMarketPriceSweepScheduler domesticMarketPriceSweepScheduler;

    // 이 클래스의 나머지 시나리오는 "리더일 때" 이후 분기를 검증하는
    // 목적(비-리더 스킵은 별도 테스트) - DomesticWatchlistPriceRelaySchedulerTest와
    // 동일 패턴.
    @BeforeEach
    void setUpLeader() {
        given(priceRelayLeaderGate.isLeader()).willReturn(true);
    }

    @Test
    @DisplayName("[리더가 아니면 아무것도 조회·발행하지 않고 스킵한다]")
    void refresh_notLeader_skipsEntirely() {
        // given
        given(priceRelayLeaderGate.isLeader()).willReturn(false);

        // when
        domesticMarketPriceSweepScheduler.refreshRanking();

        // then
        verify(domesticMarketCalendarCache, never()).isMarketOpenNow();
        verify(domesticListedStockCache, never()).get();
    }

    @Test
    @DisplayName("[장이 닫혀 있으면 종목 목록 조회도 하지 않고 스킵한다]")
    void refresh_marketClosed_skipsEntirely() {
        // given
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(false);

        // when
        domesticMarketPriceSweepScheduler.refreshRanking();

        // then
        verify(domesticListedStockCache, never()).get();
        verify(domesticMarketRankingCache, never()).update(anyList());
    }

    @Test
    @DisplayName("[상장 종목이 없으면 Toss를 호출하지 않는다]")
    void refresh_noListedStocks_skipsTossCall() {
        // given
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of());

        // when
        domesticMarketPriceSweepScheduler.refreshRanking();

        // then
        verify(tossApiClient, never()).getCurrentPrices(anyString());
    }

    @Test
    @DisplayName("[정상 틱이면 등락률을 계산해 랭킹 캐시를 갱신하고 시세를 Redis에 적재한다]")
    void refresh_normalTick_updatesRankingCacheAndPriceCache() {
        // given
        Stock stock = StockFixture.createStock(STOCK_CODE, "삼성전자");
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(domesticPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 70000.0));
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(new TossPriceResponse(
            List.of(new TossPrice(STOCK_CODE, "2026-07-06T09:00:00+09:00", "71400", "KRW"))));

        // when
        domesticMarketPriceSweepScheduler.refreshRanking();

        // then: (71400-70000)/70000*100 = 2.0
        ArgumentCaptor<List<MarketRankingResponse>> captor = ArgumentCaptor.forClass(List.class);
        verify(domesticMarketRankingCache).update(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        MarketRankingResponse ranked = captor.getValue().get(0);
        assertThat(ranked.stockCode()).isEqualTo(STOCK_CODE);
        assertThat(ranked.stockName()).isEqualTo("삼성전자");
        assertThat(ranked.currentPrice()).isEqualTo(71400L);
        assertThat(ranked.changeRate()).isEqualTo(2.0);

        // 종목당 개별 save가 아니라 청크 단위 saveAll(파이프라인)로 바뀌었다
        // (2026-08-19, PriceCacheStore.saveAll 참고).
        ArgumentCaptor<List<PriceSnapshot>> cacheCaptor = ArgumentCaptor.forClass(List.class);
        verify(priceCacheStore).saveAll(cacheCaptor.capture());
        assertThat(cacheCaptor.getValue()).hasSize(1);
        PriceSnapshot cachedSnapshot = cacheCaptor.getValue().get(0);
        assertThat(cachedSnapshot.stockCode()).isEqualTo(STOCK_CODE);
        assertThat(cachedSnapshot.currentPrice()).isEqualTo(71400L);
        assertThat(cachedSnapshot.changeRate()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("[전일 종가가 없는 종목은 랭킹에서는 제외하지만 시세 캐시에는 그대로 적재한다]")
    void refresh_noPreviousClose_excludesFromRankingButStillCachesPrice() {
        // given
        Stock stock = StockFixture.createStock(STOCK_CODE, "삼성전자");
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(domesticPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of());
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(new TossPriceResponse(
            List.of(new TossPrice(STOCK_CODE, "2026-07-06T09:00:00+09:00", "71400", "KRW"))));

        // when
        domesticMarketPriceSweepScheduler.refreshRanking();

        // then
        ArgumentCaptor<List<MarketRankingResponse>> captor = ArgumentCaptor.forClass(List.class);
        verify(domesticMarketRankingCache).update(captor.capture());
        assertThat(captor.getValue()).isEmpty();

        ArgumentCaptor<List<PriceSnapshot>> cacheCaptor = ArgumentCaptor.forClass(List.class);
        verify(priceCacheStore).saveAll(cacheCaptor.capture());
        assertThat(cacheCaptor.getValue()).hasSize(1);
        assertThat(cacheCaptor.getValue().get(0).currentPrice()).isEqualTo(71400L);
        assertThat(cacheCaptor.getValue().get(0).changeRate()).isNull();
    }

    @Test
    @DisplayName("[현재가가 정수로 파싱되지 않는 종목이 섞여 있어도 그 종목만 건너뛰고 나머지는 정상 처리한다]")
    void refresh_unparseablePrice_skipsOnlyThatSymbol() {
        // given: 한 청크 안에 정상가 종목 1개 + 파싱 불가(콤마) 종목 1개
        String badCode = "000660";
        Stock goodStock = StockFixture.createStock(STOCK_CODE, "삼성전자");
        Stock badStock = StockFixture.createStock(badCode, "SK하이닉스");
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(goodStock, badStock));
        given(domesticPreviousCloseCache.get(anyList()))
            .willReturn(Map.of(STOCK_CODE, 70000.0, badCode, 100000.0));
        given(tossApiClient.getCurrentPrices(anyString())).willReturn(new TossPriceResponse(
            List.of(
                new TossPrice(STOCK_CODE, "2026-07-06T09:00:00+09:00", "71400", "KRW"),
                new TossPrice(badCode, "2026-07-06T09:00:00+09:00", "99,000", "KRW"))));

        // when & then: 파싱 실패가 예외로 전파돼 틱/청크 전체를 죽이면 안 된다
        assertThatCode(() -> domesticMarketPriceSweepScheduler.refreshRanking())
            .doesNotThrowAnyException();

        // then: 정상 종목만 랭킹에 들어가고, 파싱 실패 종목은 시세 캐시에도 안 들어간다
        ArgumentCaptor<List<MarketRankingResponse>> captor = ArgumentCaptor.forClass(List.class);
        verify(domesticMarketRankingCache).update(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).stockCode()).isEqualTo(STOCK_CODE);
        // 두 종목이 한 청크(단일 Toss 호출)에 섞여 있으므로 saveAll도 1회만
        // 호출되고, 그 안에 담긴 스냅샷은 파싱 성공한 종목 1개뿐이어야 한다.
        ArgumentCaptor<List<PriceSnapshot>> cacheCaptor = ArgumentCaptor.forClass(List.class);
        verify(priceCacheStore, times(1)).saveAll(cacheCaptor.capture());
        assertThat(cacheCaptor.getValue()).hasSize(1);
        assertThat(cacheCaptor.getValue().get(0).stockCode()).isEqualTo(STOCK_CODE);
    }

    @Test
    @DisplayName("[Toss 호출이 실패해도 예외가 전파되지 않는다(다음 틱에 영향 없음)]")
    void refresh_tossFails_doesNotPropagate() {
        // given
        Stock stock = StockFixture.createStock(STOCK_CODE, "삼성전자");
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(domesticPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 70000.0));
        given(tossApiClient.getCurrentPrices(STOCK_CODE))
            .willThrow(new RuntimeException("토스 API 장애"));

        // when & then: SafeExecutor가 내부에서 흡수하므로 예외가 밖으로 나오면 안 됨
        assertThatCode(() -> domesticMarketPriceSweepScheduler.refreshRanking())
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[청크 하나가 ExternalApiException으로 실패하면 그 청크만 건너뛰고 사유별 카운터를 올린 뒤 다음 청크를 계속 처리한다]")
    void refresh_oneChunkFailsWithExternalApiException_skipsOnlyThatChunk() {
        // given: 201종목 = 청크 2개(청크 크기 200), 첫 호출은 레이트리밋, 두 번째는 정상(빈 결과)
        List<Stock> stocks = IntStream.range(0, 201)
            .mapToObj(i -> StockFixture.createStock(String.format("S%05d", i), "종목" + i)).toList();
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(stocks);
        given(domesticPreviousCloseCache.get(anyList())).willReturn(Map.of());
        given(tossApiClient.getCurrentPrices(anyString()))
            .willThrow(new ExternalApiException(TossApiErrorCode.RATE_LIMIT_EXCEEDED))
            .willReturn(new TossPriceResponse(List.of()));

        // when
        domesticMarketPriceSweepScheduler.refreshRanking();

        // then
        verify(tossApiClient, times(2)).getCurrentPrices(anyString());
        assertThat(meterRegistry.counter("market.sweep.chunk.skipped", "reason",
            TossApiErrorCode.RATE_LIMIT_EXCEEDED.getCode()).count()).isEqualTo(1.0);
        verify(domesticMarketRankingCache).update(List.of());
    }

    @Test
    @DisplayName("[응답 result가 null이거나 현재가가 비어 있는 심볼은 건너뛴다]")
    void refresh_nullResultOrBlankLastPrice_isSkipped() {
        // given
        Stock stock = StockFixture.createStock(STOCK_CODE, "삼성전자");
        given(domesticMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(domesticPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 70000.0));
        given(tossApiClient.getCurrentPrices(STOCK_CODE))
            .willReturn(new TossPriceResponse(null))
            .willReturn(new TossPriceResponse(List.of(new TossPrice(STOCK_CODE, "ts", " ", "KRW"))));

        // when: 두 번의 틱
        domesticMarketPriceSweepScheduler.refreshRanking();
        domesticMarketPriceSweepScheduler.refreshRanking();

        // then: 랭킹은 두 번 모두 빈 목록으로 갱신되고 시세 캐시에는 아무것도 적재되지 않는다
        verify(domesticMarketRankingCache, times(2)).update(List.of());
        verify(priceCacheStore, times(1)).saveAll(List.of());
    }
}
