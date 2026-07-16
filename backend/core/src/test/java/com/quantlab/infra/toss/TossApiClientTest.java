package com.quantlab.infra.toss;

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

import com.quantlab.common.exception.ExternalApiException;
import com.quantlab.infra.toss.dto.TossMarketCalendarResponse;
import com.quantlab.infra.toss.exception.TossApiErrorCode;
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
}
