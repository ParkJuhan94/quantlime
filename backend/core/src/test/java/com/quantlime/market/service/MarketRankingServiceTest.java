package com.quantlime.market.service;

import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.cache.TossMarketRankingCache;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.price.cache.OverseasPreviousCloseCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketRankingServiceTest {

    @Mock
    private MarketRankingCache marketRankingCache;

    @Mock
    private TossMarketRankingCache tossMarketRankingCache;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private PriceCacheStore priceCacheStore;

    @Mock
    private OverseasPreviousCloseCache overseasPreviousCloseCache;

    @InjectMocks
    private MarketRankingService marketRankingService;

    @Test
    @DisplayName("[관심종목만 보기가 아니면 국내/해외 모두 Toss 랭킹 캐시를 쓴다]")
    void getRanking_notWatchlistOnly_usesTossRankingCache() {
        // given
        given(tossMarketRankingCache.get("domestic", "gainers")).willReturn(
            List.of(ranking("005930", 2.0)));

        // when
        List<MarketRankingResponse> result = marketRankingService.getRanking("domestic", "gainers", 10, null);

        // then
        assertThat(result).extracting(MarketRankingResponse::stockCode).containsExactly("005930");
        verify(marketRankingCache, never()).getGainers(anyInt(), any());
    }

    @Test
    @DisplayName("[국내 + 관심종목만 보기 + gainers는 기존 자체 계산 캐시를 쓴다]")
    void getRanking_domesticWatchlistOnlyGainers_usesMarketRankingCache() {
        // given
        Set<String> watchlistCodes = Set.of("005930");
        given(marketRankingCache.getGainers(10, watchlistCodes)).willReturn(
            List.of(ranking("005930", 3.0)));

        // when
        List<MarketRankingResponse> result =
            marketRankingService.getRanking("domestic", "gainers", 10, watchlistCodes);

        // then
        assertThat(result).extracting(MarketRankingResponse::stockCode).containsExactly("005930");
        verify(tossMarketRankingCache, never()).get(any(), any());
    }

    @Test
    @DisplayName("[국내 + 관심종목만 보기 + losers는 기존 자체 계산 캐시(losers)를 쓴다]")
    void getRanking_domesticWatchlistOnlyLosers_usesMarketRankingCacheLosers() {
        // given
        Set<String> watchlistCodes = Set.of("035420");
        given(marketRankingCache.getLosers(10, watchlistCodes)).willReturn(
            List.of(ranking("035420", -3.0)));

        // when
        List<MarketRankingResponse> result =
            marketRankingService.getRanking("domestic", "losers", 10, watchlistCodes);

        // then
        assertThat(result).extracting(MarketRankingResponse::stockCode).containsExactly("035420");
    }

    @Test
    @DisplayName("[관심종목만 보기 + amount 정렬은 top100 제약이 있는 Toss 캐시를 쓰고 관심종목으로 필터링한다]")
    void getRanking_watchlistOnlyAmountSort_usesTossCacheFilteredByWatchlist() {
        // given
        Set<String> watchlistCodes = Set.of("005930");
        given(tossMarketRankingCache.get("domestic", "amount")).willReturn(
            List.of(ranking("005930", 1.0), ranking("000660", 2.0)));

        // when
        List<MarketRankingResponse> result =
            marketRankingService.getRanking("domestic", "amount", 10, watchlistCodes);

        // then
        assertThat(result).extracting(MarketRankingResponse::stockCode).containsExactly("005930");
    }

    @Test
    @DisplayName("[해외 + 관심종목만 보기 + gainers는 캐시된 시세로 자체 계산해 등락률 내림차순 정렬한다]")
    void getRanking_overseasWatchlistOnlyGainers_computesFromPriceCacheDescending() {
        // given
        Set<String> watchlistCodes = Set.of("AAPL", "MSFT");
        Stock aapl = overseasStock("AAPL", "Apple");
        Stock msft = overseasStock("MSFT", "Microsoft");
        given(stockRepository.findByStockCodeIn(anyList())).willReturn(List.of(aapl, msft));
        given(overseasPreviousCloseCache.get(anyList())).willReturn(
            Map.of("AAPL", 340.0, "MSFT", 400.0));
        given(priceCacheStore.find("AAPL")).willReturn(
            Optional.of(new PriceSnapshot("AAPL", 341.43, null, "t")));
        given(priceCacheStore.find("MSFT")).willReturn(
            Optional.of(new PriceSnapshot("MSFT", 397.0, null, "t"))); // 하락

        // when
        List<MarketRankingResponse> result =
            marketRankingService.getRanking("overseas", "gainers", 10, watchlistCodes);

        // then: AAPL은 상승(+), MSFT는 하락(-) - 상승 먼저
        assertThat(result).extracting(MarketRankingResponse::stockCode).containsExactly("AAPL", "MSFT");
        assertThat(result.get(0).currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("[해외 관심종목 중 시세 캐시가 없는 종목(스케줄러 미도달/장마감)은 결과에서 제외한다]")
    void getRanking_overseasWatchlistOnly_excludesCacheMiss() {
        // given
        Set<String> watchlistCodes = Set.of("AAPL");
        Stock aapl = overseasStock("AAPL", "Apple");
        given(stockRepository.findByStockCodeIn(anyList())).willReturn(List.of(aapl));
        given(overseasPreviousCloseCache.get(anyList())).willReturn(Map.of("AAPL", 340.0));
        given(priceCacheStore.find("AAPL")).willReturn(Optional.empty());

        // when
        List<MarketRankingResponse> result =
            marketRankingService.getRanking("overseas", "gainers", 10, watchlistCodes);

        // then
        assertThat(result).isEmpty();
    }

    private MarketRankingResponse ranking(String stockCode, double changeRate) {
        return new MarketRankingResponse(stockCode, stockCode + "-name", "전기전자",
            10000.0, changeRate, "KRW", null, null);
    }

    private Stock overseasStock(String stockCode, String stockName) {
        return Stock.of(stockCode, stockName, MarketType.NASDAQ, ListingStatus.LISTED, null);
    }
}
