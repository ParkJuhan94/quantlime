package com.quantlime.infra.tosspayments;

import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.tosspayments.dto.TossBillingKeyResponse;
import com.quantlime.infra.tosspayments.dto.TossPaymentApprovalResponse;
import com.quantlime.infra.tosspayments.exception.TossPaymentsErrorCode;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class TossPaymentsApiClient {

    private final RestClient tossPaymentsRestClient;

    public TossBillingKeyResponse issueBillingKey(String customerKey, String authKey) {
        return ExternalApiInvoker.call(
            TossPaymentsErrorCode.BILLING_KEY_ISSUANCE_FAILED,
            () -> tossPaymentsRestClient.post()
                .uri("/v1/billing/authorizations/issue")
                .body(Map.of("customerKey", customerKey, "authKey", authKey))
                .retrieve()
                .body(TossBillingKeyResponse.class),
            HttpClientErrorException.TooManyRequests.class,
            TossPaymentsErrorCode.RATE_LIMIT_EXCEEDED);
    }

    // installmentMonths: 0=일시불, 2~12=할부개월. 최초 가입 결제와 매 갱신
    // 결제가 모두 이 메서드 하나를 공유한다(호출부에서 renewal 여부만 다름).
    public TossPaymentApprovalResponse chargeWithBillingKey(
        String billingKey, String customerKey, String orderId, String orderName,
        int amount, int installmentMonths) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerKey", customerKey);
        body.put("orderId", orderId);
        body.put("orderName", orderName);
        body.put("amount", amount);
        if (installmentMonths > 0) {
            body.put("cardInstallmentPlan", installmentMonths);
        }
        return ExternalApiInvoker.call(
            TossPaymentsErrorCode.PAYMENT_CHARGE_FAILED,
            () -> tossPaymentsRestClient.post()
                .uri("/v1/billing/{billingKey}", billingKey)
                .body(body)
                .retrieve()
                .body(TossPaymentApprovalResponse.class),
            HttpClientErrorException.TooManyRequests.class,
            TossPaymentsErrorCode.RATE_LIMIT_EXCEEDED);
    }

    // 환불 정책은 이번 범위 밖 - 시그니처만 마련해두고 실제 호출부(해지 시
    // 일할 환불 등)는 별도 요청 때 붙인다.
    public TossPaymentApprovalResponse cancelPayment(String paymentKey, String cancelReason) {
        return ExternalApiInvoker.call(
            TossPaymentsErrorCode.PAYMENT_CANCEL_FAILED,
            () -> tossPaymentsRestClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .body(Map.of("cancelReason", cancelReason))
                .retrieve()
                .body(TossPaymentApprovalResponse.class),
            HttpClientErrorException.TooManyRequests.class,
            TossPaymentsErrorCode.RATE_LIMIT_EXCEEDED);
    }
}
