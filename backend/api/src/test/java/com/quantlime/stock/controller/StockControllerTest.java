package com.quantlime.stock.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.stock.StockFixture;
import com.quantlime.stock.dto.response.StockFundamentalsResponse;
import com.quantlime.stock.repository.StockRepository;
import com.quantlime.stock.service.StockFundamentalsService;
import com.quantlime.support.MockedServicesApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("integration")
class StockControllerTest extends MockedServicesApiTestSupport {

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        stockRepository.save(StockFixture.createStock("005930", "삼성전자"));
    }

    @Test
    @DisplayName("[종목 상세는 로그인 없이 조회되고 종목 정보를 반환한다]")
    void getStock_returnsDetail() throws Exception {
        mockMvc.perform(get("/api/stocks/{code}", "005930"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockCode").value("005930"))
            .andExpect(jsonPath("$.stockName").value("삼성전자"))
            .andExpect(jsonPath("$.marketType").value("코스피"));
    }

    @Test
    @DisplayName("[없는 종목 코드는 404]")
    void getStock_unknown_returns404() throws Exception {
        mockMvc.perform(get("/api/stocks/{code}", "999999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[밸류에이션 지표는 서비스 결과를 그대로 반환하고, 값이 없는 항목은 null이다]")
    void getFundamentals_returnsNullableFields() throws Exception {
        given(stockFundamentalsService.getFundamentals("005930"))
            .willReturn(new StockFundamentalsResponse(4.5e14, 12.3, null, 1.1, null, 30.0));

        mockMvc.perform(get("/api/stocks/{code}/fundamentals", "005930"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.per").value(12.3))
            .andExpect(jsonPath("$.forwardPer").doesNotExist())
            .andExpect(jsonPath("$.debtRatio").value(30.0));
    }

    @Test
    @DisplayName("[검색어가 비어 있으면 400, 종목명 검색은 결과를 반환한다]")
    void searchStocks_blankQuery400_nameQueryFinds() throws Exception {
        mockMvc.perform(get("/api/stocks/search").param("q", "")).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/stocks/search").param("q", "삼성"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].stockCode").value("005930"));
    }

    @Test
    @DisplayName("[인기 종목 limit는 1~20 범위만 허용한다]")
    void getPopularStocks_limitBounds() throws Exception {
        mockMvc.perform(get("/api/stocks/popular").param("limit", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/stocks/popular").param("limit", "21")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/stocks/popular").param("limit", "5")).andExpect(status().isOk());
    }
}
