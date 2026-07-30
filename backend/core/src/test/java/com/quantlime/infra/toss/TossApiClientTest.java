package com.quantlime.infra.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.toss.dto.TossMarketCalendarResponse;
import com.quantlime.infra.toss.dto.TossRankingResponse;
import com.quantlime.infra.toss.dto.TossUsMarketCalendarResponse;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@Tag("unit")
class TossApiClientTest {

    private static final String BASE_URL = "https://toss.test";
    private static final String CALENDAR_URI = BASE_URL + "/api/v1/market-calendar/KR";
    private static final String PRICES_URI = BASE_URL + "/api/v1/prices?symbols=005930";

    private MockRestServiceServer mockServer;
    private TossTokenManager tokenManager;
    private TossApiClient tossApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        tokenManager = mock(TossTokenManager.class);
        tossApiClient = new TossApiClient(restClient, tokenManager, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("[401 응답을 받으면 토큰을 무효화하고 재발급받아 1회 재시도한다]")
    void withTokenRetry_unauthorized_invalidatesAndRetriesOnce() {
        // given
        when(tokenManager.getAccessToken()).thenReturn("stale-token", "fresh-token");
        mockServer.expect(requestTo(CALENDAR_URI))
            .andExpect(method(GET))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"invalid-token\"}}"));
        mockServer.expect(requestTo(CALENDAR_URI))
            .andExpect(method(GET))
            .andRespond(withSuccess(
                "{\"result\":{\"today\":{\"date\":\"2026-07-13\",\"integrated\":null}}}",
                MediaType.APPLICATION_JSON));

        // when
        TossMarketCalendarResponse response = tossApiClient.getMarketCalendar();

        // then
        assertThat(response.result().today().date()).isEqualTo("2026-07-13");
        verify(tokenManager, times(1)).invalidateToken();
        mockServer.verify();
    }

    @Test
    @DisplayName("[재발급받은 토큰마저 401이면 예외를 그대로 전파한다]")
    void withTokenRetry_unauthorizedTwice_throwsExternalApiException() {
        // given
        when(tokenManager.getAccessToken()).thenReturn("stale-token", "still-stale-token");
        mockServer.expect(requestTo(CALENDAR_URI))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"invalid-token\"}}"));
        mockServer.expect(requestTo(CALENDAR_URI))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"invalid-token\"}}"));

        // when & then
        assertThatThrownBy(() -> tossApiClient.getMarketCalendar())
            .isInstanceOf(ExternalApiException.class);
        verify(tokenManager, times(1)).invalidateToken();
        mockServer.verify();
    }

    @Test
    @DisplayName("[429(Rate Limit) 응답은 토큰을 무효화하지 않고 RATE_LIMIT_EXCEEDED로 전파한다]")
    void withTokenRetry_tooManyRequests_doesNotInvalidateToken() {
        // given
        when(tokenManager.getAccessToken()).thenReturn("token");
        mockServer.expect(requestTo(PRICES_URI))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"rate-limit\"}}"));

        // when & then
        assertThatThrownBy(() -> tossApiClient.getCurrentPrices("005930"))
            .isInstanceOf(ExternalApiException.class)
            .hasFieldOrPropertyWithValue("code", TossApiErrorCode.RATE_LIMIT_EXCEEDED.getCode());
        verify(tokenManager, never()).invalidateToken();
        mockServer.verify();
    }

    @Test
    @DisplayName("[랭킹 조회 시 type/marketCountry/duration/count 쿼리 파라미터를 그대로 전달한다]")
    void getRankings_success_buildsQueryParamsAndParsesResponse() {
        // given
        when(tokenManager.getAccessToken()).thenReturn("token");
        String uri = BASE_URL
            + "/api/v1/rankings?type=TOP_GAINERS&marketCountry=US&duration=1d&count=10";
        mockServer.expect(requestTo(uri))
            .andExpect(method(GET))
            .andRespond(withSuccess(
                "{\"result\":{\"rankedAt\":\"2026-07-29T17:43:34+09:00\",\"rankings\":"
                    + "[{\"rank\":1,\"symbol\":\"AAPL\",\"currency\":\"USD\","
                    + "\"price\":{\"lastPrice\":\"341.43\",\"basePrice\":\"340\",\"changeRate\":\"0.0042\"},"
                    + "\"tradingVolume\":\"84921\",\"tradingAmount\":\"29000000\"}]}}",
                MediaType.APPLICATION_JSON));

        // when
        TossRankingResponse response = tossApiClient.getRankings("TOP_GAINERS", "US", "1d", 10);

        // then
        assertThat(response.result().rankings()).hasSize(1);
        assertThat(response.result().rankings().get(0).symbol()).isEqualTo("AAPL");
        mockServer.verify();
    }

    @Test
    @DisplayName("[해외 장 운영 캘린더 조회 시 3영업일 응답을 그대로 파싱한다]")
    void getUsMarketCalendar_success_parsesThreeBusinessDays() {
        // given
        when(tokenManager.getAccessToken()).thenReturn("token");
        String uri = BASE_URL + "/api/v1/market-calendar/US";
        mockServer.expect(requestTo(uri))
            .andExpect(method(GET))
            .andRespond(withSuccess(
                "{\"result\":{"
                    + "\"previousBusinessDay\":{\"date\":\"2026-07-28\",\"dayMarket\":null,"
                    + "\"preMarket\":null,\"regularMarket\":{\"startTime\":\"2026-07-28T22:30:00+09:00\","
                    + "\"endTime\":\"2026-07-29T05:00:00+09:00\"},\"afterMarket\":null},"
                    + "\"today\":{\"date\":\"2026-07-29\",\"dayMarket\":null,\"preMarket\":null,"
                    + "\"regularMarket\":{\"startTime\":\"2026-07-29T22:30:00+09:00\","
                    + "\"endTime\":\"2026-07-30T05:00:00+09:00\"},\"afterMarket\":null},"
                    + "\"nextBusinessDay\":{\"date\":\"2026-07-30\",\"dayMarket\":null,"
                    + "\"preMarket\":null,\"regularMarket\":null,\"afterMarket\":null}}}",
                MediaType.APPLICATION_JSON));

        // when
        TossUsMarketCalendarResponse response = tossApiClient.getUsMarketCalendar();

        // then
        assertThat(response.result().today().date()).isEqualTo("2026-07-29");
        assertThat(response.result().previousBusinessDay().regularMarket().startTime())
            .isEqualTo("2026-07-28T22:30:00+09:00");
        mockServer.verify();
    }

    @Test
    @DisplayName("[getDailyCandles를 연달아 호출하면 최소 간격만큼 대기한다 - "
        + "국내/해외 스윕이 별도 스레드에서 같은 MARKET_DATA_CHART 예산을 나눠 쓰므로 "
        + "호출측이 몇 개든 결합 호출률을 여기서 강제해야 한다]")
    void getDailyCandles_consecutiveCalls_paceCombinedCallRate() {
        // given
        when(tokenManager.getAccessToken()).thenReturn("token");
        String candlesUri = BASE_URL + "/api/v1/candles?symbol=005930&interval=1d&count=10&adjusted=true";
        String candleBody = "{\"result\":{\"candles\":[],\"nextBefore\":null}}";
        mockServer.expect(requestTo(candlesUri))
            .andExpect(method(GET))
            .andRespond(withSuccess(candleBody, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(candlesUri))
            .andExpect(method(GET))
            .andRespond(withSuccess(candleBody, MediaType.APPLICATION_JSON));

        // when
        long start = System.currentTimeMillis();
        tossApiClient.getDailyCandles("005930", 10, null);
        tossApiClient.getDailyCandles("005930", 10, null);
        long elapsedMs = System.currentTimeMillis() - start;

        // then: 두 번째 호출이 최소 간격(150ms)만큼 지연됐어야 한다
        assertThat(elapsedMs).isGreaterThanOrEqualTo(140);
        mockServer.verify();
    }
}
