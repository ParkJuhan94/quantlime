package com.quantlime.market.controller;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.market.cache.BitcoinChartCache;
import com.quantlime.market.cache.ExchangeRateChartCache;
import com.quantlime.market.cache.IndexChartCache;
import com.quantlime.market.cache.IndexMinuteChartCache;
import com.quantlime.market.cache.MarketIndexCache;
import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.cache.WorldIndexChartCache;
import com.quantlime.market.dto.response.IndexChartResponse;
import com.quantlime.market.dto.response.IndexMinuteChartResponse;
import com.quantlime.market.dto.response.MarketIndexResponse;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import com.quantlime.watchlist.service.WatchlistService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MarketIndexCache/MarketRankingCache/IndexChartCache는 짧은 TTL을 가진
 * 상태 저장 빈이라(같은 스프링 컨텍스트를 공유하는 다른 테스트가 채워둔
 * 값이 남아있을 수 있음) 직접 목으로 대체한다 - PriceControllerTest가
 * PriceCacheStore를 목으로 대체하는 것과 동일한 이유. 캐시 자체의
 * 갱신/TTL 로직은 MarketIndexCacheTest/MarketRankingCacheTest에서 이미
 * 검증한다.
 */
@Tag("integration")
class MarketControllerTest extends ApiTestSupport {

    @MockBean
    private MarketIndexCache marketIndexCache;

    @MockBean
    private MarketRankingCache marketRankingCache;

    @MockBean
    private IndexChartCache indexChartCache;

    @MockBean
    private IndexMinuteChartCache indexMinuteChartCache;

    @MockBean
    private WorldIndexChartCache worldIndexChartCache;

    @MockBean
    private BitcoinChartCache bitcoinChartCache;

    @MockBean
    private ExchangeRateChartCache exchangeRateChartCache;

    @MockBean
    private WatchlistService watchlistService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("[주요 지수 조회 성공 시 200과 환율·비트코인·코스피/코스닥/해외지수 시세를 반환한다]")
    void getIndices_success_returns200() throws Exception {
        // given
        given(marketIndexCache.get()).willReturn(
            new MarketIndexResponse(1380.5, "UP", -0.25, 132000000L, 3.45, 4.59, 0.04, List.of(4.55, 4.59),
                new MarketIndexResponse.IndexQuote(7284.41, 427.58, 6.24, false, null, null, null),
                new MarketIndexResponse.IndexQuote(829.43, 45.45, 5.80, true, null, null, null),
                new MarketIndexResponse.IndexQuote(26201.58, 94.58, 0.36, true, null, null, null),
                new MarketIndexResponse.IndexQuote(7564.47, 20.88, 0.28, true, null, null, null),
                new MarketIndexResponse.IndexQuote(12600.99, -60.94, -0.48, false, 12500.10, -1.28, "AFTER_MARKET")));

        // when & then
        mockMvc.perform(get("/api/market/indices"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.usdKrwRate").value(1380.5))
            .andExpect(jsonPath("$.usdKrwChangeType").value("UP"))
            .andExpect(jsonPath("$.usdKrwChangeRate").value(-0.25))
            .andExpect(jsonPath("$.bitcoinPriceKrw").value(132000000))
            .andExpect(jsonPath("$.bitcoinChangeRate").value(3.45))
            .andExpect(jsonPath("$.usTreasuryYield10y").value(4.59))
            .andExpect(jsonPath("$.usTreasuryYield10yChangeRate").value(0.04))
            .andExpect(jsonPath("$.usTreasuryYield10yHistory").isArray())
            .andExpect(jsonPath("$.kospi.value").value(7284.41))
            .andExpect(jsonPath("$.kospi.marketOpen").value(false))
            .andExpect(jsonPath("$.kosdaq.marketOpen").value(true))
            .andExpect(jsonPath("$.nasdaq.value").value(26201.58))
            .andExpect(jsonPath("$.sp500.value").value(7564.47))
            .andExpect(jsonPath("$.soxx.marketOpen").value(false))
            .andExpect(jsonPath("$.soxx.overMarketValue").value(12500.10))
            .andExpect(jsonPath("$.soxx.overMarketChangeRate").value(-1.28))
            .andExpect(jsonPath("$.soxx.overMarketSessionType").value("AFTER_MARKET"));
    }

    @Test
    @DisplayName("[코스피 차트 조회 성공 시 200을 반환한다]")
    void getIndexChart_kospi_returns200() throws Exception {
        // given
        given(indexChartCache.get("KOSPI")).willReturn(
            List.of(new IndexChartResponse(LocalDate.of(2026, 7, 15), 7082.91, 7424.18, 7082.91, 7284.41)));

        // when & then
        mockMvc.perform(get("/api/market/indices/{code}/chart", "KOSPI"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].close").value(7284.41));
    }

    @Test
    @DisplayName("[나스닥 차트 조회 성공 시 해외지수 캐시를 로이터 코드로 조회한다]")
    void getIndexChart_nasdaq_usesWorldIndexCacheWithReutersCode() throws Exception {
        // given
        given(worldIndexChartCache.get(".IXIC", false)).willReturn(
            List.of(new IndexChartResponse(LocalDate.of(2026, 7, 15), 26015.49, 26300.00, 25900.00, 26201.58)));

        // when & then
        mockMvc.perform(get("/api/market/indices/{code}/chart", "NASDAQ"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].close").value(26201.58));
    }

    @Test
    @DisplayName("[지수 차트 code가 지원하는 값이 아니면 400을 반환한다]")
    void getIndexChart_invalidCode_returns400() throws Exception {
        mockMvc.perform(get("/api/market/indices/{code}/chart", "DOWJONES"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[코스피 당일 분봉 차트 조회 성공 시 200을 반환한다]")
    void getIndexMinuteChart_kospi_returns200() throws Exception {
        // given
        given(indexMinuteChartCache.get("KOSPI")).willReturn(
            List.of(new IndexMinuteChartResponse(LocalDateTime.of(2026, 7, 15, 9, 0, 0), 7095.79)));

        // when & then
        mockMvc.perform(get("/api/market/indices/{code}/minute-chart", "KOSPI"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].price").value(7095.79));
    }

    @Test
    @DisplayName("[분봉 차트 code가 KOSPI/KOSDAQ가 아니면 400을 반환한다]")
    void getIndexMinuteChart_invalidCode_returns400() throws Exception {
        mockMvc.perform(get("/api/market/indices/{code}/minute-chart", "NASDAQ"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[비트코인 최근 24시간 차트 조회 성공 시 200을 반환한다]")
    void getBitcoinChart_success_returns200() throws Exception {
        // given
        given(bitcoinChartCache.get()).willReturn(
            List.of(new IndexMinuteChartResponse(LocalDateTime.of(2026, 7, 15, 22, 0, 0), 95959000.0)));

        // when & then
        mockMvc.perform(get("/api/market/indices/bitcoin/minute-chart"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].price").value(95959000.0));
    }

    @Test
    @DisplayName("[달러 환율 차트 조회 성공 시 200을 반환한다]")
    void getExchangeRateChart_success_returns200() throws Exception {
        // given
        given(exchangeRateChartCache.get()).willReturn(
            List.of(new IndexChartResponse(LocalDate.of(2026, 7, 15), 1487.5, 1487.5, 1487.5, 1487.5)));

        // when & then
        mockMvc.perform(get("/api/market/indices/usdkrw/chart"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].close").value(1487.5));
    }

    @Test
    @DisplayName("[급상승 랭킹 조회 성공 시 200을 반환한다]")
    void getRanking_gainers_returns200() throws Exception {
        // given
        given(marketRankingCache.getGainers(10, null)).willReturn(
            List.of(new MarketRankingResponse("005930", "삼성전자", "전기전자", 71400L, 2.0)));

        // when & then
        mockMvc.perform(get("/api/market/ranking").param("sort", "gainers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].stockCode").value("005930"))
            .andExpect(jsonPath("$[0].changeRate").value(2.0));
    }

    @Test
    @DisplayName("[급하락 랭킹 조회 시 losers 캐시를 사용한다]")
    void getRanking_losers_usesLosersCache() throws Exception {
        // given
        given(marketRankingCache.getLosers(5, null)).willReturn(
            List.of(new MarketRankingResponse("035420", "NAVER", "서비스업", 100000L, -4.5)));

        // when & then
        mockMvc.perform(get("/api/market/ranking")
                .param("sort", "losers")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].stockCode").value("035420"));
    }

    @Test
    @DisplayName("[sort 값이 gainers/losers가 아니면 400을 반환한다]")
    void getRanking_invalidSort_returns400() throws Exception {
        mockMvc.perform(get("/api/market/ranking").param("sort", "amount"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[limit이 범위를 벗어나면 400을 반환한다]")
    void getRanking_limitOutOfRange_returns400() throws Exception {
        mockMvc.perform(get("/api/market/ranking").param("limit", "100"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[watchlistOnly=true + 로그인 시 관심종목 코드로 필터링해 조회한다]")
    void getRanking_watchlistOnlyWithAuth_filtersByWatchlistCodes() throws Exception {
        // given
        User user = userRepository.save(UserFixture.createUser());
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        given(watchlistService.getWatchlistStockCodes(user.getId())).willReturn(Set.of("005930"));
        given(marketRankingCache.getGainers(10, Set.of("005930"))).willReturn(
            List.of(new MarketRankingResponse("005930", "삼성전자", "전기전자", 71400L, 2.0)));

        // when & then
        mockMvc.perform(get("/api/market/ranking")
                .param("watchlistOnly", "true")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].stockCode").value("005930"));
    }

    @Test
    @DisplayName("[watchlistOnly=true인데 비로그인이면 빈 배열을 반환한다]")
    void getRanking_watchlistOnlyWithoutAuth_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/market/ranking").param("watchlistOnly", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }
}
