package com.quantlime.infra.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 단순 조회 엔드포인트들의 요청 조립(경로·쿼리·Bearer 헤더)과 실패 매핑(에러코드·호출 메트릭) 검증.
 * 토큰 재발급과 캔들 레이트리밋 재시도처럼 흐름이 복잡한 경로는 {@link TossApiClientTest}가 맡는다.
 */
@Tag("unit")
class TossApiClientEndpointsTest {

    private static final String BASE_URL = "https://toss.test";

    private MockRestServiceServer mockServer;
    private SimpleMeterRegistry meterRegistry;
    private TossApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        TossTokenManager tokenManager = mock(TossTokenManager.class);
        when(tokenManager.getAccessToken()).thenReturn("tok");
        meterRegistry = new SimpleMeterRegistry();
        client = new TossApiClient(builder.build(), tokenManager, meterRegistry);
    }

    private void expectGet(String uri, String body) {
        mockServer.expect(requestTo(BASE_URL + uri))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("authorization", "Bearer tok"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void expectStatus(String uri, HttpStatus status) {
        mockServer.expect(requestTo(BASE_URL + uri))
            .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body("{}"));
    }

    private double calls(String endpoint, String outcome) {
        return meterRegistry.counter("toss.api.calls", "endpoint", endpoint, "outcome", outcome).count();
    }

    @Test
    @DisplayName("[단순 조회 엔드포인트는 경로·쿼리·Bearer 헤더를 조립하고 성공 메트릭을 남긴다]")
    void simpleEndpoints_buildExpectedRequests() {
        expectGet("/api/v1/stocks?symbols=005930,000660", "{}");
        expectGet("/api/v1/prices?symbols=005930", "{}");
        expectGet("/api/v1/price-limits?symbol=005930", "{}");
        expectGet("/api/v1/stocks/005930/warnings", "{}");
        expectGet("/api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW", "{}");
        expectGet("/api/v1/market-indicators/prices?symbols=KOSPI,KOSDAQ", "{}");

        assertThat(client.getStockInfo("005930,000660")).isNotNull();
        assertThat(client.getCurrentPrices("005930")).isNotNull();
        assertThat(client.getPriceLimits("005930")).isNotNull();
        assertThat(client.getStockWarnings("005930")).isNotNull();
        assertThat(client.getExchangeRate("USD", "KRW")).isNotNull();
        assertThat(client.getMarketIndicatorPrices("KOSPI,KOSDAQ")).isNotNull();

        mockServer.verify();
        assertThat(calls("stocks", "success")).isEqualTo(1.0);
        assertThat(calls("exchange-rate", "success")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[시장 지표 캔들과 투자자 매매대금은 커서 파라미터가 있을 때만 쿼리에 붙인다]")
    void marketIndicatorEndpoints_cursorIsOptional() {
        expectGet("/api/v1/market-indicators/KOSPI/candles?interval=1d&count=30", "{}");
        expectGet("/api/v1/market-indicators/KOSPI/candles?interval=1d&count=30&before=2026-09-01", "{}");
        expectGet("/api/v1/market-indicators/KOSPI/investor-trading?interval=1d&count=5", "{}");
        expectGet("/api/v1/market-indicators/KOSPI/investor-trading?interval=1d&count=5&until=cursor-1", "{}");

        assertThat(client.getMarketIndicatorCandles("KOSPI", "1d", 30, null)).isNotNull();
        assertThat(client.getMarketIndicatorCandles("KOSPI", "1d", 30, "2026-09-01")).isNotNull();
        assertThat(client.getInvestorTrading("KOSPI", "1d", 5, null)).isNotNull();
        assertThat(client.getInvestorTrading("KOSPI", "1d", 5, "cursor-1")).isNotNull();

        mockServer.verify();
    }

    @Test
    @DisplayName("[서버 오류는 엔드포인트별 에러코드의 ExternalApiException으로 바꾸고 error 메트릭을 남긴다]")
    void serverError_mapsToEndpointSpecificErrorCode() {
        expectStatus("/api/v1/stocks?symbols=005930", HttpStatus.INTERNAL_SERVER_ERROR);
        expectStatus("/api/v1/prices?symbols=005930", HttpStatus.INTERNAL_SERVER_ERROR);
        expectStatus("/api/v1/price-limits?symbol=005930", HttpStatus.INTERNAL_SERVER_ERROR);
        expectStatus("/api/v1/stocks/005930/warnings", HttpStatus.INTERNAL_SERVER_ERROR);
        expectStatus("/api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW", HttpStatus.INTERNAL_SERVER_ERROR);
        expectStatus("/api/v1/market-indicators/prices?symbols=KOSPI", HttpStatus.INTERNAL_SERVER_ERROR);
        expectStatus("/api/v1/market-indicators/KOSPI/candles?interval=1d&count=1", HttpStatus.INTERNAL_SERVER_ERROR);
        expectStatus("/api/v1/market-indicators/KOSPI/investor-trading?interval=1d&count=1",
            HttpStatus.INTERNAL_SERVER_ERROR);

        assertFails(() -> client.getStockInfo("005930"), TossApiErrorCode.STOCK_INFO_INQUIRY_FAILED);
        assertFails(() -> client.getCurrentPrices("005930"), TossApiErrorCode.PRICE_INQUIRY_FAILED);
        assertFails(() -> client.getPriceLimits("005930"), TossApiErrorCode.PRICE_LIMIT_INQUIRY_FAILED);
        assertFails(() -> client.getStockWarnings("005930"), TossApiErrorCode.STOCK_WARNING_INQUIRY_FAILED);
        assertFails(() -> client.getExchangeRate("USD", "KRW"), TossApiErrorCode.EXCHANGE_RATE_INQUIRY_FAILED);
        assertFails(() -> client.getMarketIndicatorPrices("KOSPI"),
            TossApiErrorCode.MARKET_INDICATOR_PRICE_INQUIRY_FAILED);
        assertFails(() -> client.getMarketIndicatorCandles("KOSPI", "1d", 1, null),
            TossApiErrorCode.MARKET_INDICATOR_CANDLE_INQUIRY_FAILED);
        assertFails(() -> client.getInvestorTrading("KOSPI", "1d", 1, null),
            TossApiErrorCode.INVESTOR_TRADING_INQUIRY_FAILED);

        assertThat(calls("stocks", "error")).isEqualTo(1.0);
        assertThat(calls("investor-trading", "error")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[429는 RATE_LIMIT_EXCEEDED로 매핑하고 rate_limited 메트릭을 남긴다]")
    void tooManyRequests_mapsToRateLimitExceeded() {
        expectStatus("/api/v1/prices?symbols=005930", HttpStatus.TOO_MANY_REQUESTS);

        assertFails(() -> client.getCurrentPrices("005930"), TossApiErrorCode.RATE_LIMIT_EXCEEDED);
        assertThat(calls("prices", "rate_limited")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[일봉은 before가 있으면 쿼리에 붙이고, 분봉은 interval=1m으로 조회한다]")
    void candleEndpoints_includeOptionalBefore() {
        expectGet("/api/v1/candles?symbol=005930&interval=1d&count=20&adjusted=true&before=cursor-1", "{}");
        expectGet("/api/v1/candles?symbol=005930&interval=1m&count=1&before=2026-09-01T06:30:00Z", "{}");

        assertThat(client.getDailyCandles("005930", 20, "cursor-1")).isNotNull();
        assertThat(client.get1MinuteCandleBefore("005930", "2026-09-01T06:30:00Z")).isNotNull();

        mockServer.verify();
    }

    private void assertFails(Runnable call, TossApiErrorCode expected) {
        Consumer<Throwable> check = t -> assertThat(((ExternalApiException) t).getCode()).isEqualTo(expected.getCode());
        assertThatThrownBy(call::run).isInstanceOf(ExternalApiException.class).satisfies(check);
    }
}
