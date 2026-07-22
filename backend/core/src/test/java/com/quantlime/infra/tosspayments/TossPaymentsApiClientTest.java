package com.quantlime.infra.tosspayments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.tosspayments.dto.TossBillingKeyResponse;
import com.quantlime.infra.tosspayments.dto.TossPaymentApprovalResponse;
import com.quantlime.infra.tosspayments.exception.TossPaymentsErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@Tag("unit")
class TossPaymentsApiClientTest {

    private static final String BASE_URL = "https://tosspayments.test";

    private MockRestServiceServer mockServer;
    private TossPaymentsApiClient tossPaymentsApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        tossPaymentsApiClient = new TossPaymentsApiClient(builder.build());
    }

    @Test
    @DisplayName("[빌링키 발급에 성공하면 응답을 그대로 반환한다]")
    void issueBillingKey_success_returnsBillingKey() {
        // given
        mockServer.expect(requestTo(BASE_URL + "/v1/billing/authorizations/issue"))
            .andExpect(method(POST))
            .andRespond(withSuccess(
                "{\"billingKey\":\"bk-1\",\"customerKey\":\"customer-1\","
                    + "\"cardCompany\":\"국민\",\"cardNumber\":\"1234\",\"authenticatedAt\":\"now\"}",
                MediaType.APPLICATION_JSON));

        // when
        TossBillingKeyResponse response = tossPaymentsApiClient.issueBillingKey("customer-1", "auth-key");

        // then
        assertThat(response.billingKey()).isEqualTo("bk-1");
    }

    @Test
    @DisplayName("[할부개월을 지정하면 cardInstallmentPlan을 요청 바디에 포함한다]")
    void chargeWithBillingKey_withInstallment_includesCardInstallmentPlan() {
        // given
        mockServer.expect(requestTo(BASE_URL + "/v1/billing/bk-1"))
            .andExpect(method(POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"cardInstallmentPlan\":6")))
            .andRespond(withSuccess(
                "{\"paymentKey\":\"pk-1\",\"orderId\":\"order-1\",\"orderName\":\"구독\","
                    + "\"status\":\"DONE\",\"totalAmount\":94800,\"method\":\"카드\",\"approvedAt\":\"now\"}",
                MediaType.APPLICATION_JSON));

        // when
        TossPaymentApprovalResponse response = tossPaymentsApiClient.chargeWithBillingKey(
            "bk-1", "customer-1", "order-1", "구독", 94800, 6);

        // then
        assertThat(response.paymentKey()).isEqualTo("pk-1");
        mockServer.verify();
    }

    @Test
    @DisplayName("[429 응답을 받으면 RATE_LIMIT_EXCEEDED로 매핑한다]")
    void chargeWithBillingKey_tooManyRequests_mapsToRateLimitExceeded() {
        // given
        mockServer.expect(requestTo(BASE_URL + "/v1/billing/bk-1"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"EXCEED_MAX_AUTHENTICATION_COUNT\"}"));

        // when & then
        assertThatThrownBy(() -> tossPaymentsApiClient.chargeWithBillingKey(
            "bk-1", "customer-1", "order-1", "구독", 7900, 0))
            .isInstanceOf(ExternalApiException.class)
            .satisfies(e -> assertThat(((ExternalApiException) e).getCode())
                .isEqualTo(TossPaymentsErrorCode.RATE_LIMIT_EXCEEDED.getCode()));
    }
}
