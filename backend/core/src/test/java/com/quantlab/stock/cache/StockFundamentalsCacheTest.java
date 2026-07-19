package com.quantlab.stock.cache;

import com.quantlab.infra.naver.NaverFinanceApiClient;
import com.quantlab.infra.naver.dto.NaverStockFinanceAnnualResponse;
import com.quantlab.infra.naver.dto.NaverStockFinanceAnnualResponse.ColumnValue;
import com.quantlab.infra.naver.dto.NaverStockFinanceAnnualResponse.FinanceInfo;
import com.quantlab.infra.naver.dto.NaverStockFinanceAnnualResponse.Row;
import com.quantlab.infra.naver.dto.NaverStockFinanceAnnualResponse.TrTitle;
import com.quantlab.infra.naver.dto.NaverStockIntegrationResponse;
import com.quantlab.infra.naver.dto.NaverStockIntegrationResponse.TotalInfo;
import com.quantlab.stock.dto.response.StockFundamentalsResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockFundamentalsCacheTest {

    @Mock
    private NaverFinanceApiClient naverFinanceApiClient;

    @InjectMocks
    private StockFundamentalsCache stockFundamentalsCache;

    @Test
    @DisplayName("[한국어 단위(조/억) 시총과 배수 표기를 파싱하고 최근 확정연도 재무로 부채비율·PSR을 계산한다]")
    void get_parsesKoreanUnitsAndComputesPsrFromLatestActualYear() {
        // given
        given(naverFinanceApiClient.getStockIntegration("005930")).willReturn(
            new NaverStockIntegrationResponse(List.of(
                new TotalInfo("marketValue", "시총", "1,634조 349억"),
                new TotalInfo("per", "PER", "22.59배"),
                new TotalInfo("cnsPer", "추정PER", "5.99배"),
                new TotalInfo("pbr", "PBR", "3.89배"))));
        given(naverFinanceApiClient.getStockFinanceAnnual("005930")).willReturn(
            new NaverStockFinanceAnnualResponse(new FinanceInfo(
                List.of(
                    new TrTitle("202412", "2024.12.", "N"),
                    new TrTitle("202512", "2025.12.", "N"),
                    new TrTitle("202612", "2026.12.", "Y")),
                List.of(
                    new Row("매출액", Map.of(
                        "202412", new ColumnValue("3,008,709"),
                        "202512", new ColumnValue("3,336,059"),
                        "202612", new ColumnValue("7,324,732"))),
                    new Row("부채비율", Map.of(
                        "202412", new ColumnValue("27.93"),
                        "202512", new ColumnValue("29.94"),
                        "202612", new ColumnValue("-")))))));

        // when
        StockFundamentalsResponse result = stockFundamentalsCache.get("005930");

        // then: 시총 = 1,634조 + 349억 = 1,634,000,000,000,000 + 34,900,000,000
        assertThat(result.marketCap()).isEqualTo(1_634_034_900_000_000.0);
        assertThat(result.per()).isEqualTo(22.59);
        assertThat(result.forwardPer()).isEqualTo(5.99);
        assertThat(result.pbr()).isEqualTo(3.89);
        // 최근 확정(N) 연도는 202512(202612는 컨센서스라 제외)
        assertThat(result.debtRatio()).isEqualTo(29.94);
        double expectedRevenue = 3_336_059d * 100_000_000;
        assertThat(result.psr()).isEqualTo(result.marketCap() / expectedRevenue);
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 외부 API를 다시 호출하지 않는다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(naverFinanceApiClient.getStockIntegration("005930")).willReturn(
            new NaverStockIntegrationResponse(List.of(new TotalInfo("per", "PER", "22.59배"))));
        given(naverFinanceApiClient.getStockFinanceAnnual("005930")).willReturn(
            new NaverStockFinanceAnnualResponse(new FinanceInfo(List.of(), List.of())));

        // when
        stockFundamentalsCache.get("005930");
        stockFundamentalsCache.get("005930");

        // then
        verify(naverFinanceApiClient, times(1)).getStockIntegration("005930");
    }

    @Test
    @DisplayName("[외부 API 호출이 실패하면 해당 필드만 null로 응답한다]")
    void get_apiFails_returnsNullFields() {
        // given
        given(naverFinanceApiClient.getStockIntegration("005930")).willThrow(new RuntimeException("boom"));
        given(naverFinanceApiClient.getStockFinanceAnnual("005930")).willThrow(new RuntimeException("boom"));

        // when
        StockFundamentalsResponse result = stockFundamentalsCache.get("005930");

        // then
        assertThat(result.marketCap()).isNull();
        assertThat(result.per()).isNull();
        assertThat(result.psr()).isNull();
    }
}
