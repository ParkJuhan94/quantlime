package com.quantlime.price.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.price.dto.response.OrderbookResponse;
import com.quantlime.price.dto.response.StockWarningResponse;
import com.quantlime.price.service.StockMarketDepthService;
import com.quantlime.support.ApiTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

@Tag("integration")
class StockMarketDepthControllerTest extends ApiTestSupport {

    @MockBean
    private StockMarketDepthService stockMarketDepthService;

    @Test
    @DisplayName("[호가 조회 시 로그인 없이 200과 호가를 반환한다]")
    void getOrderbook_success_returns200() throws Exception {
        // given
        given(stockMarketDepthService.getOrderbook("005930")).willReturn(new OrderbookResponse(
            null, "KRW",
            List.of(new OrderbookResponse.Level(72100.0, 8500.0)),
            List.of(new OrderbookResponse.Level(72000.0, 100.0))));

        // when & then
        mockMvc.perform(get("/api/stocks/{stockCode}/orderbook", "005930"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.asks[0].price").value(72100.0))
            .andExpect(jsonPath("$.bids[0].volume").value(100.0));
    }

    @Test
    @DisplayName("[유의사항 조회 시 200과 한글 라벨 목록을 반환한다]")
    void getWarnings_success_returns200() throws Exception {
        // given
        given(stockMarketDepthService.getWarnings("005930")).willReturn(List.of(
            new StockWarningResponse("OVERHEATED", "단기과열종목", "KRX", "2026-03-26", null)));

        // when & then
        mockMvc.perform(get("/api/stocks/{stockCode}/warnings", "005930"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].label").value("단기과열종목"));
    }
}
