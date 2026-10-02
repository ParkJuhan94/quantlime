package com.quantlime.infra.naver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.naver.dto.NaverExchangeRateCandleResponse;
import com.quantlime.infra.naver.dto.NaverIndexBasicResponse;
import com.quantlime.infra.naver.dto.NaverIndexCandleResponse;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 네이버 금융 클라이언트는 국내(m.stock.naver.com)·해외/분봉/환율(api.stock.naver.com) 두
 * 호스트를 쓴다 - 경로가 어느 RestClient로 나가는지가 이 클래스의 핵심 계약이라 두 서버를
 * 따로 바인딩해 경로 라우팅까지 검증한다.
 */
@Tag("unit")
class NaverFinanceApiClientTest {

    private static final String MOBILE_URL = "https://m.naver.test";
    private static final String CHART_URL = "https://api.naver.test";

    private MockRestServiceServer mobileServer;
    private MockRestServiceServer chartServer;
    private NaverFinanceApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder mobile = RestClient.builder().baseUrl(MOBILE_URL);
        RestClient.Builder chart = RestClient.builder().baseUrl(CHART_URL);
        mobileServer = MockRestServiceServer.bindTo(mobile).build();
        chartServer = MockRestServiceServer.bindTo(chart).build();
        client = new NaverFinanceApiClient(mobile.build(), chart.build());
    }

    private static final String BASIC_JSON = """
        {"stockName":"코스피","closePrice":"7,284.41","compareToPreviousClosePrice":"12.3",
         "fluctuationsRatio":"0.17","marketStatus":"CLOSE","localTradedAt":"2026-09-30T15:30:00+09:00","unknown":1}
        """;

    @Test
    @DisplayName("[국내 지수 basic은 모바일 호스트로, 콤마 포맷 문자열 원본을 그대로 담는다]")
    void getIndexBasic_usesMobileHost_keepsRawStrings() {
        mobileServer.expect(requestTo(MOBILE_URL + "/api/index/KOSPI/basic"))
            .andRespond(withSuccess(BASIC_JSON, MediaType.APPLICATION_JSON));

        NaverIndexBasicResponse response = client.getIndexBasic("KOSPI");

        assertThat(response.stockName()).isEqualTo("코스피");
        assertThat(response.closePrice()).isEqualTo("7,284.41");
        assertThat(response.overMarketPriceInfo()).isNull();
        mobileServer.verify();
    }

    @Test
    @DisplayName("[해외 지수 basic은 차트 호스트(로이터 코드)로 호출한다]")
    void getWorldIndexBasic_usesChartHost() {
        chartServer.expect(requestTo(CHART_URL + "/index/.IXIC/basic"))
            .andRespond(withSuccess(BASIC_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.getWorldIndexBasic(".IXIC").stockName()).isEqualTo("코스피");
        chartServer.verify();
    }

    @Test
    @DisplayName("[해외 ETF는 /stock 경로, 환율 현재값은 /marketindex/exchange 경로로 차트 호스트에 나간다]")
    void worldStockAndExchangeBasic_paths() {
        chartServer.expect(requestTo(CHART_URL + "/stock/SOXX.O/basic"))
            .andRespond(withSuccess(BASIC_JSON, MediaType.APPLICATION_JSON));
        chartServer.expect(requestTo(CHART_URL + "/marketindex/exchange/FX_USDKRW"))
            .andRespond(withSuccess("{\"closePrice\":\"1,380.5\",\"fluctuationsRatio\":\"0.1\"}",
                MediaType.APPLICATION_JSON));

        client.getWorldStockBasic("SOXX.O");
        assertThat(client.getExchangeRateBasic("FX_USDKRW")).isNotNull();
        chartServer.verify();
    }

    @Test
    @DisplayName("[지수 일봉은 pageSize/page 쿼리를 싣고, 기본 오버로드는 page=1이다]")
    void getIndexPrices_pagination() {
        String json = "[{\"localTradedAt\":\"2026-09-30\",\"closePrice\":\"7,000\",\"openPrice\":\"6,900\","
            + "\"highPrice\":\"7,100\",\"lowPrice\":\"6,800\"}]";
        mobileServer.expect(requestTo(Matchers.startsWith(MOBILE_URL + "/api/index/KOSPI/price")))
            .andExpect(queryParam("pageSize", "60"))
            .andExpect(queryParam("page", "1"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        mobileServer.expect(requestTo(Matchers.startsWith(MOBILE_URL + "/api/index/KOSPI/price")))
            .andExpect(queryParam("page", "3"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<NaverIndexCandleResponse> first = client.getIndexPrices("KOSPI", 60);
        List<NaverIndexCandleResponse> third = client.getIndexPrices("KOSPI", 60, 3);

        assertThat(first).hasSize(1);
        assertThat(first.get(0).closePrice()).isEqualTo("7,000");
        assertThat(third).isEmpty();
        mobileServer.verify();
    }

    @Test
    @DisplayName("[환율 일별 이력은 pair 경로와 page=1 고정 쿼리로 조회한다]")
    void getExchangeRatePrices_query() {
        chartServer.expect(requestTo(Matchers.startsWith(CHART_URL + "/marketindex/exchange/FX_USDKRW/prices")))
            .andExpect(queryParam("pageSize", "30"))
            .andExpect(queryParam("page", "1"))
            .andRespond(withSuccess("[{\"localTradedAt\":\"2026-09-30\",\"closePrice\":\"1,380.5\"}]",
                MediaType.APPLICATION_JSON));

        List<NaverExchangeRateCandleResponse> prices = client.getExchangeRatePrices("FX_USDKRW", 30);

        assertThat(prices).extracting(NaverExchangeRateCandleResponse::closePrice).containsExactly("1,380.5");
        chartServer.verify();
    }

    @Test
    @DisplayName("[서버 오류는 ExternalApiException으로 감싸 실패를 호출측 폴백에 맡긴다]")
    void serverError_wrapsAsExternalApiException() {
        mobileServer.expect(requestTo(MOBILE_URL + "/api/index/KOSPI/basic")).andRespond(withServerError());
        chartServer.expect(requestTo(Matchers.startsWith(CHART_URL + "/chart/domestic/index/KOSPI/minute")))
            .andRespond(withSuccess());

        assertThatThrownBy(() -> client.getIndexBasic("KOSPI")).isInstanceOf(ExternalApiException.class);
        assertThatThrownBy(() -> client.getIndexMinuteCandles("KOSPI")).isInstanceOf(ExternalApiException.class);
    }
}
