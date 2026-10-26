package com.quantlime.tradingflow.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.common.exception.GlobalExceptionHandler;
import com.quantlime.tradingflow.dto.response.StockInvestorTradingResponse;
import com.quantlime.tradingflow.service.StockInvestorTradingService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 라우팅·기본 파라미터·응답 모양만 검증한다. 전체 컨텍스트를 띄우지 않는 standalone 방식이라 컨텍스트 캐시에 영향이
 * 없다(권한은 SecurityConfig 몫, count 범위 검증은 메서드 검증 AOP가 필요해 여기서 다루지 않는다).
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockInvestorTradingControllerTest {

    @Mock
    private StockInvestorTradingService stockInvestorTradingService;

    @InjectMocks
    private StockInvestorTradingController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("[count를 생략하면 20일치를 조회하고 순매수·확정 여부를 200으로 돌려준다]")
    void getInvestorTrading_defaultCount_returns200() throws Exception {
        given(stockInvestorTradingService.getRecent("005930", 20)).willReturn(List.of(
            new StockInvestorTradingResponse(LocalDate.of(2026, 10, 23), -200L, 100L, 100L, null, null, 0.5089, false)));

        mockMvc.perform(get("/api/stocks/005930/investor-trading"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].tradeDate").value("2026-10-23"))
            .andExpect(jsonPath("$[0].individualNetBuyVolume").value(-200))
            .andExpect(jsonPath("$[0].foreignerNetBuyVolume").value(100))
            .andExpect(jsonPath("$[0].otherCorporationNetBuyVolume").doesNotExist())
            .andExpect(jsonPath("$[0].institutionBreakdown").doesNotExist())
            .andExpect(jsonPath("$[0].confirmed").value(false));
    }

    @Test
    @DisplayName("[확정 기록은 기관 세부 7개 분류 순매수를 이름으로 내려준다]")
    void getInvestorTrading_confirmed_includesInstitutionBreakdown() throws Exception {
        given(stockInvestorTradingService.getRecent("005930", 20)).willReturn(List.of(
            new StockInvestorTradingResponse(LocalDate.of(2026, 10, 22), -200L, 100L, 100L, 5L,
                new StockInvestorTradingResponse.InstitutionBreakdownResponse(1L, 2L, 3L, 4L, 5L, 6L, 7L),
                0.5089, true)));

        mockMvc.perform(get("/api/stocks/005930/investor-trading"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].institutionBreakdown.financialInvestmentNetBuyVolume").value(1))
            .andExpect(jsonPath("$[0].institutionBreakdown.pensionFundNetBuyVolume").value(7))
            .andExpect(jsonPath("$[0].confirmed").value(true));
    }

    @Test
    @DisplayName("[count를 주면 그 값으로 조회한다]")
    void getInvestorTrading_explicitCount_passesThrough() throws Exception {
        given(stockInvestorTradingService.getRecent("005930", 60)).willReturn(List.of());

        mockMvc.perform(get("/api/stocks/005930/investor-trading").param("count", "60"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }
}
