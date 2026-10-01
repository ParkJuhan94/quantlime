package com.quantlime.payment.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.tosspayments.TossPaymentsApiClient;
import com.quantlime.infra.tosspayments.TossPaymentsProperties;
import com.quantlime.infra.tosspayments.TossWebhookVerifier;
import com.quantlime.infra.tosspayments.dto.TossBillingKeyResponse;
import com.quantlime.infra.tosspayments.dto.TossPaymentApprovalResponse;
import com.quantlime.infra.tosspayments.exception.TossPaymentsErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BillingProcessorTest {

    @Mock
    private TossPaymentsApiClient tossPaymentsApiClient;

    @Mock
    private TossWebhookVerifier tossWebhookVerifier;

    @Mock
    private TossPaymentsProperties tossPaymentsProperties;

    @InjectMocks
    private BillingProcessor billingProcessor;

    @Test
    @DisplayName("[빌링키 발급은 TossPaymentsApiClient에 그대로 위임한다]")
    void issueBillingKey_delegatesToClient() {
        // given
        TossBillingKeyResponse response = new TossBillingKeyResponse("bk-1", "customer-1", "국민", "1234", "now");
        given(tossPaymentsApiClient.issueBillingKey("customer-1", "auth-key")).willReturn(response);

        // when
        TossBillingKeyResponse result = billingProcessor.issueBillingKey("customer-1", "auth-key");

        // then
        assertThat(result).isEqualTo(response);
    }

    @Test
    @DisplayName("[과금은 TossPaymentsApiClient에 그대로 위임한다]")
    void charge_delegatesToClient() {
        // given
        TossPaymentApprovalResponse response = new TossPaymentApprovalResponse(
            "pk-1", "order-1", "구독", "DONE", 10000, "카드", "now");
        given(tossPaymentsApiClient.chargeWithBillingKey(
            "bk-1", "customer-1", "order-1", "구독", 10000, 0)).willReturn(response);

        // when
        TossPaymentApprovalResponse result =
            billingProcessor.charge("bk-1", "customer-1", "order-1", "구독", 10000, 0);

        // then
        assertThat(result).isEqualTo(response);
    }

    @Test
    @DisplayName("[웹훅 서명 검증은 설정된 시크릿과 함께 TossWebhookVerifier에 위임한다]")
    void verifyWebhookSignature_delegatesWithConfiguredSecret() {
        // given
        given(tossPaymentsProperties.getWebhookSecret()).willReturn("secret");
        given(tossWebhookVerifier.verify("payload", "signature", "secret")).willReturn(true);

        // when
        boolean valid = billingProcessor.verifyWebhookSignature("payload", "signature");

        // then
        assertThat(valid).isTrue();
        verify(tossWebhookVerifier).verify("payload", "signature", "secret");
    }

    @Test
    @DisplayName("[카드 거절 등 HTTP 4xx는 업무적 거절로 분류한다]")
    void isBusinessDecline_httpClientError_true() {
        // given
        HttpClientErrorException badRequest = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null);
        ExternalApiException e = new ExternalApiException(TossPaymentsErrorCode.PAYMENT_CHARGE_FAILED, badRequest);

        // when & then
        assertThat(billingProcessor.isBusinessDecline(e)).isTrue();
    }

    @Test
    @DisplayName("[429(요청 한도 초과)는 HttpClientErrorException의 하위 타입이어도 업무적 거절로 "
        + "분류하지 않는다 - 카드 거절이 아니라 토스 API 요청 한도를 넘었다는 뜻이라 재시도하면 "
        + "성공할 수 있다]")
    void isBusinessDecline_tooManyRequests_false() {
        // given
        HttpClientErrorException.TooManyRequests tooManyRequests =
            (HttpClientErrorException.TooManyRequests) HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);
        ExternalApiException e = new ExternalApiException(TossPaymentsErrorCode.RATE_LIMIT_EXCEEDED, tooManyRequests);

        // when & then
        assertThat(billingProcessor.isBusinessDecline(e)).isFalse();
    }

    @Test
    @DisplayName("[타임아웃/연결 실패처럼 HttpClientErrorException이 아닌 원인은 업무적 거절로 분류하지 않는다]")
    void isBusinessDecline_nonHttpCause_false() {
        // given
        ExternalApiException e = new ExternalApiException(
            TossPaymentsErrorCode.PAYMENT_CHARGE_FAILED, new ResourceAccessException("connect timed out"));

        // when & then
        assertThat(billingProcessor.isBusinessDecline(e)).isFalse();
    }
}
