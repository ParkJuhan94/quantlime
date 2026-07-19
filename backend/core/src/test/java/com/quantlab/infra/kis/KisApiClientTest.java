package com.quantlab.infra.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlab.common.exception.ExternalApiException;
import com.quantlab.infra.kis.dto.KisOverseasPriceResponse;
import com.quantlab.infra.kis.exception.KisApiErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@Tag("unit")
class KisApiClientTest {

    private static final String BASE_URL = "https://kis.test";
    private static final String PRICE_URI =
        BASE_URL + "/uapi/overseas-price/v1/quotations/price?AUTH=&EXCD=NAS&SYMB=AAPL";
    private static final String DAILY_PRICE_URI =
        BASE_URL + "/uapi/overseas-price/v1/quotations/dailyprice"
            + "?AUTH=&EXCD=NAS&SYMB=AAPL&GUBN=0&MODP=1&BYMD=";

    private MockRestServiceServer mockServer;
    private KisApiProperties properties;
    private KisTokenManager tokenManager;
    private KisApiClient kisApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        properties = new KisApiProperties("app-key", "app-secret", BASE_URL, BASE_URL);
        tokenManager = mock(KisTokenManager.class);
        kisApiClient = new KisApiClient(restClient, properties, tokenManager);
    }

    @Test
    @DisplayName("[정상 응답 시 appkey/appsecret/tr_id 헤더를 실어 현재가를 조회한다]")
    void getOverseasPrice_success_appliesRequiredHeaders() {
        // given
        when(tokenManager.getAccessToken()).thenReturn("access-token");
        mockServer.expect(requestTo(PRICE_URI))
            .andExpect(method(GET))
            .andExpect(header("authorization", "Bearer access-token"))
            .andExpect(header("appkey", "app-key"))
            .andExpect(header("appsecret", "app-secret"))
            .andExpect(header("tr_id", "HHDFS00000300"))
            .andRespond(withSuccess(
                "{\"rt_cd\":\"0\",\"msg1\":\"OK\",\"output\":{\"last\":\"151.23\"}}",
                MediaType.APPLICATION_JSON));

        // when
        KisOverseasPriceResponse response = kisApiClient.getOverseasPrice("NAS", "AAPL");

        // then
        assertThat(response.output().last()).isEqualTo("151.23");
        mockServer.verify();
    }

    @Test
    @DisplayName("[일별시세 조회 시 tr_id/수정주가 파라미터를 고정으로 실어 요청한다]")
    void getOverseasDailyPrice_success_fixesModpAndGubn() {
        // given
        when(tokenManager.getAccessToken()).thenReturn("access-token");
        mockServer.expect(requestTo(DAILY_PRICE_URI))
            .andExpect(header("tr_id", "HHDFS76240000"))
            .andRespond(withSuccess(
                "{\"rt_cd\":\"0\",\"msg1\":\"OK\",\"output2\":[]}",
                MediaType.APPLICATION_JSON));

        // when
        var response = kisApiClient.getOverseasDailyPrice("NAS", "AAPL", null);

        // then
        assertThat(response.output2()).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("[401 응답을 받으면 토큰을 무효화하고 재발급받아 1회 재시도한다]")
    void withTokenRetry_unauthorized_invalidatesAndRetriesOnce() {
        // given
        when(tokenManager.getAccessToken()).thenReturn("stale-token", "fresh-token");
        mockServer.expect(requestTo(PRICE_URI))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"rt_cd\":\"1\",\"msg1\":\"invalid token\"}"));
        mockServer.expect(requestTo(PRICE_URI))
            .andRespond(withSuccess(
                "{\"rt_cd\":\"0\",\"msg1\":\"OK\",\"output\":{\"last\":\"151.23\"}}",
                MediaType.APPLICATION_JSON));

        // when
        KisOverseasPriceResponse response = kisApiClient.getOverseasPrice("NAS", "AAPL");

        // then
        assertThat(response.output().last()).isEqualTo("151.23");
        verify(tokenManager, times(1)).invalidateToken();
        mockServer.verify();
    }

    @Test
    @DisplayName("[429(Rate Limit) 응답은 토큰을 무효화하지 않고 RATE_LIMIT_EXCEEDED로 전파한다]")
    void withTokenRetry_tooManyRequests_doesNotInvalidateToken() {
        // given
        when(tokenManager.getAccessToken()).thenReturn("access-token");
        mockServer.expect(requestTo(PRICE_URI))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"rt_cd\":\"1\",\"msg1\":\"rate limit\"}"));

        // when & then
        assertThatThrownBy(() -> kisApiClient.getOverseasPrice("NAS", "AAPL"))
            .isInstanceOf(ExternalApiException.class)
            .hasFieldOrPropertyWithValue("code", KisApiErrorCode.RATE_LIMIT_EXCEEDED.getCode());
        verify(tokenManager, never()).invalidateToken();
        mockServer.verify();
    }
}
