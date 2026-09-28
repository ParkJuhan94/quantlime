package com.quantlime.market.service;

import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.cache.TossMarketRankingCache;
import com.quantlime.market.dto.response.MarketRankingResponse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 국내/해외 둘 다 MarketRankingCache로 통일된 뒤(2026-09-25) 이 서비스는
// "어느 캐시로 라우팅하는가"만 남았다 - 실제 랭킹 계산(Stock 조회/등락률
// 산출)은 DomesticMarketPriceSweepScheduler(국내)/
// OverseasWatchlistPriceScheduler(해외)로 옮겨갔다.
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketRankingServiceTest {

    @Mock
    private MarketRankingCache domesticMarketRankingCache;

    @Mock
    private MarketRankingCache overseasMarketRankingCache;

    @Mock
    private TossMarketRankingCache tossMarketRankingCache;

    private MarketRankingService marketRankingService;

    // domesticMarketRankingCache/overseasMarketRankingCache가 정확히 같은
    // 타입(MarketRankingCache)이라 @InjectMocks의 리플렉션 기반 생성자
    // 매칭이 파라미터 이름을 보고 안전하게 구분한다는 보장이 없다(실제로
    // 두 목이 뒤바뀌어 주입되는 걸 확인함) - 생성자를 직접 호출해 어떤 목이
    // 어느 자리에 들어가는지 명시적으로 고정한다.
    @BeforeEach
    void setUp() {
        marketRankingService =
            new MarketRankingService(domesticMarketRankingCache, overseasMarketRankingCache, tossMarketRankingCache);
    }

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
        verify(domesticMarketRankingCache, never()).getGainers(anyInt(), any());
    }

    @Test
    @DisplayName("[국내 + 관심종목만 보기 + gainers는 domesticMarketRankingCache를 쓴다]")
    void getRanking_domesticWatchlistOnlyGainers_usesDomesticCache() {
        // given
        Set<String> watchlistCodes = Set.of("005930");
        given(domesticMarketRankingCache.getGainers(10, watchlistCodes)).willReturn(
            List.of(ranking("005930", 3.0)));

        // when
        List<MarketRankingResponse> result =
            marketRankingService.getRanking("domestic", "gainers", 10, watchlistCodes);

        // then
        assertThat(result).extracting(MarketRankingResponse::stockCode).containsExactly("005930");
        verify(tossMarketRankingCache, never()).get(any(), any());
        verify(overseasMarketRankingCache, never()).getGainers(anyInt(), any());
    }

    @Test
    @DisplayName("[국내 + 관심종목만 보기 + losers는 domesticMarketRankingCache의 losers를 쓴다]")
    void getRanking_domesticWatchlistOnlyLosers_usesDomesticCacheLosers() {
        // given
        Set<String> watchlistCodes = Set.of("035420");
        given(domesticMarketRankingCache.getLosers(10, watchlistCodes)).willReturn(
            List.of(ranking("035420", -3.0)));

        // when
        List<MarketRankingResponse> result =
            marketRankingService.getRanking("domestic", "losers", 10, watchlistCodes);

        // then
        assertThat(result).extracting(MarketRankingResponse::stockCode).containsExactly("035420");
    }

    @Test
    @DisplayName("[해외 + 관심종목만 보기 + gainers는 overseasMarketRankingCache를 쓴다]")
    void getRanking_overseasWatchlistOnlyGainers_usesOverseasCache() {
        // given
        Set<String> watchlistCodes = Set.of("AAPL");
        given(overseasMarketRankingCache.getGainers(10, watchlistCodes)).willReturn(
            List.of(ranking("AAPL", 1.5)));

        // when
        List<MarketRankingResponse> result =
            marketRankingService.getRanking("overseas", "gainers", 10, watchlistCodes);

        // then
        assertThat(result).extracting(MarketRankingResponse::stockCode).containsExactly("AAPL");
        verify(domesticMarketRankingCache, never()).getGainers(anyInt(), any());
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

    private MarketRankingResponse ranking(String stockCode, double changeRate) {
        return new MarketRankingResponse(stockCode, stockCode + "-name", "전기전자",
            10000.0, changeRate, "KRW", null, null, null, true);
    }
}
