package com.quantlab.price.controller;

import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.price.DailyPriceFixture;
import com.quantlab.price.repository.DailyPriceRepository;
import com.quantlab.stock.StockFixture;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.repository.StockRepository;
import com.quantlab.support.ApiTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class PriceControllerTest extends ApiTestSupport {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DailyPriceRepository dailyPriceRepository;

    @MockBean
    private TossApiClient tossApiClient;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = stockRepository.save(StockFixture.createStock());
    }

    @Test
    @DisplayName("[현재가 조회 성공 시 200과 가격을 반환한다]")
    void getCurrentPrice_success_returns200() throws Exception {
        // given
        TossPriceResponse.TossPrice tossPrice = new TossPriceResponse.TossPrice(
            stock.getStockCode(), "2026-07-06T09:00:00+09:00", "70000", "KRW");
        given(tossApiClient.getCurrentPrices(stock.getStockCode()))
            .willReturn(new TossPriceResponse(List.of(tossPrice)));

        // when & then
        mockMvc.perform(get("/api/stocks/{stockCode}/price", stock.getStockCode()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").value(70000))
            .andExpect(jsonPath("$.currency").value("KRW"));
    }

    @Test
    @DisplayName("[현재가가 없으면 200과 price=null을 반환한다]")
    void getCurrentPrice_noPrice_returnsNullPrice() throws Exception {
        // given
        given(tossApiClient.getCurrentPrices(stock.getStockCode()))
            .willReturn(new TossPriceResponse(List.of()));

        // when & then
        mockMvc.perform(get("/api/stocks/{stockCode}/price", stock.getStockCode()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").doesNotExist());
    }

    @Test
    @DisplayName("[존재하지 않는 종목의 현재가를 조회하면 404를 반환한다]")
    void getCurrentPrice_unknownStock_returns404() throws Exception {
        mockMvc.perform(get("/api/stocks/{stockCode}/price", "999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[차트 조회 시 저장된 일별 시세를 반환한다]")
    void getChart_returnsStoredDailyPrices() throws Exception {
        // given
        dailyPriceRepository.save(
            DailyPriceFixture.createDailyPrice(stock.getStockCode(), LocalDate.now()));

        // when & then
        mockMvc.perform(get("/api/stocks/{stockCode}/chart", stock.getStockCode())
                .param("period", "daily")
                .param("days", "30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].close").value(105));
    }

    @Test
    @DisplayName("[period가 daily가 아니면 400을 반환한다]")
    void getChart_invalidPeriod_returns400() throws Exception {
        mockMvc.perform(get("/api/stocks/{stockCode}/chart", stock.getStockCode())
                .param("period", "weekly"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[days가 범위를 벗어나면 400을 반환한다]")
    void getChart_daysOutOfRange_returns400() throws Exception {
        mockMvc.perform(get("/api/stocks/{stockCode}/chart", stock.getStockCode())
                .param("days", "1000"))
            .andExpect(status().isBadRequest());
    }
}
