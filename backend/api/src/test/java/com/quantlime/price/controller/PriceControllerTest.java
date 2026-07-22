package com.quantlime.price.controller;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.price.DailyPriceFixture;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.repository.DailyPriceRepository;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import com.quantlime.support.ApiTestSupport;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.anyString;
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

    // Redis는 TestContainerSupport가 격리하지만, @EnableScheduling이
    // 테스트 프로파일에서도 그대로 켜져 있어 MarketPriceSweepScheduler가
    // 백그라운드에서 같은 컨테이너에 price:current:{stockCode}를 비동기로
    // 채울 수 있다(타이밍에 따라 있을 수도 없을 수도 있음). 이 컨트롤러
    // 테스트는 캐시를 항상 미스로 고정해 Toss 응답 매핑 자체만
    // 결정적으로 검증한다(캐시 히트 경로는 StockPriceServiceTest에서
    // 별도 검증).
    @MockBean
    private PriceCacheStore priceCacheStore;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = stockRepository.save(StockFixture.createStock());
        given(priceCacheStore.find(anyString())).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("[현재가 조회 성공 시 200과 DB의 마지막 종가를 반환한다]")
    void getCurrentPrice_success_returns200() throws Exception {
        // given: 캐시 미스 경로는 더 이상 Toss를 직접 호출하지 않고 DB의
        // 마지막 확정 종가로 응답한다(429 반복 발생 원인이었던 무페이싱
        // 직접 호출 제거, 2026-07-17). DailyPriceFixture 종가는 105L 고정값
        dailyPriceRepository.save(DailyPriceFixture.createDailyPrice(stock.getStockCode(), LocalDate.now()));

        // when & then
        mockMvc.perform(get("/api/stocks/{stockCode}/price", stock.getStockCode()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").value(105))
            .andExpect(jsonPath("$.currency").value("KRW"));
    }

    @Test
    @DisplayName("[DB에 시세 이력이 없으면 200과 price=null을 반환한다]")
    void getCurrentPrice_noPrice_returnsNullPrice() throws Exception {
        // when & then: DailyPrice 이력을 저장하지 않은 상태 그대로 조회
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
