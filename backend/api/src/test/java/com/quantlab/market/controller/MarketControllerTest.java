package com.quantlab.market.controller;

import com.quantlab.market.cache.MarketIndexCache;
import com.quantlab.market.cache.MarketRankingCache;
import com.quantlab.market.dto.response.MarketIndexResponse;
import com.quantlab.market.dto.response.MarketRankingResponse;
import com.quantlab.support.ApiTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MarketIndexCache/MarketRankingCache는 짧은 TTL을 가진 상태 저장 빈이라
 * (같은 스프링 컨텍스트를 공유하는 다른 테스트가 채워둔 값이 남아있을 수
 * 있음) 직접 목으로 대체한다 - PriceControllerTest가 PriceCacheStore를
 * 목으로 대체하는 것과 동일한 이유. 캐시 자체의 갱신/TTL 로직은
 * MarketIndexCacheTest/MarketRankingCacheTest에서 이미 검증한다.
 */
@Tag("integration")
class MarketControllerTest extends ApiTestSupport {

    @MockBean
    private MarketIndexCache marketIndexCache;

    @MockBean
    private MarketRankingCache marketRankingCache;

    @Test
    @DisplayName("[주요 지수 조회 성공 시 200과 환율·비트코인 시세를 반환한다]")
    void getIndices_success_returns200() throws Exception {
        // given
        given(marketIndexCache.get()).willReturn(
            new MarketIndexResponse(1380.5, "UP", 132000000L, 3.45));

        // when & then
        mockMvc.perform(get("/api/market/indices"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.usdKrwRate").value(1380.5))
            .andExpect(jsonPath("$.usdKrwChangeType").value("UP"))
            .andExpect(jsonPath("$.bitcoinPriceKrw").value(132000000))
            .andExpect(jsonPath("$.bitcoinChangeRate").value(3.45));
    }

    @Test
    @DisplayName("[급상승 랭킹 조회 성공 시 200을 반환한다]")
    void getRanking_gainers_returns200() throws Exception {
        // given
        given(marketRankingCache.getGainers(10)).willReturn(
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
        given(marketRankingCache.getLosers(5)).willReturn(
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
}
