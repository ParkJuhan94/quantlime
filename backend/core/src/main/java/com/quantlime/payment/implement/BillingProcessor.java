package com.quantlime.payment.implement;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.tosspayments.TossPaymentsApiClient;
import com.quantlime.infra.tosspayments.TossPaymentsProperties;
import com.quantlime.infra.tosspayments.TossWebhookVerifier;
import com.quantlime.infra.tosspayments.dto.TossBillingKeyResponse;
import com.quantlime.infra.tosspayments.dto.TossPaymentApprovalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * 토스페이먼츠 API 호출(빌링키 발급/과금/웹훅 검증)과 그 응답 해석을 감싸는
 * 구현 레이어(Implementation) - {@code PaymentService}가 {@code TossPaymentsApiClient}
 * 등 infra 클라이언트를 직접 알지 않고, "결제를 처리한다"는 흐름만 오케스트레이션
 * 하도록 이 컴포넌트 뒤로 감춘다.
 */
@Component
@RequiredArgsConstructor
public class BillingProcessor {

    private final TossPaymentsApiClient tossPaymentsApiClient;
    private final TossWebhookVerifier tossWebhookVerifier;
    private final TossPaymentsProperties tossPaymentsProperties;

    public TossBillingKeyResponse issueBillingKey(String customerKey, String authKey) {
        return tossPaymentsApiClient.issueBillingKey(customerKey, authKey);
    }

    public TossPaymentApprovalResponse charge(
        String billingKey, String customerKey, String orderId, String orderName,
        int amount, int installmentMonths) {
        return tossPaymentsApiClient.chargeWithBillingKey(
            billingKey, customerKey, orderId, orderName, amount, installmentMonths);
    }

    public boolean verifyWebhookSignature(String payload, String signatureHeader) {
        return tossWebhookVerifier.verify(payload, signatureHeader, tossPaymentsProperties.getWebhookSecret());
    }

    /**
     * HTTP 4xx(카드 거절 등 업무적 거절)인지 판단한다 - 5xx/타임아웃/연결
     * 실패와 달리 몇 초~몇 분 뒤 재시도해도 같은 결과가 나오므로 카프카
     * 재시도 대상에서 제외한다(호출부 {@code PaymentService.chargeRenewal} 참고).
     *
     * <p>429(Too Many Requests)는 예외로 둔다 - {@code HttpClientErrorException}의
     * 하위 타입이라 단순 instanceof 검사로는 카드 거절과 구분되지 않지만,
     * "카드사가 거절했다"가 아니라 "토스 API 요청 한도를 넘었다"는 뜻이라
     * 성격이 완전히 다르다. 몇 초~몇 분 뒤 재시도하면 성공할 수 있는
     * 일시적 신호이므로 카프카 재시도로 넘겨야 한다 - 카드 거절로
     * 오분류하면 재시도 없이 곧장 실패 처리+PAST_DUE 카운트가 올라가는
     * 버그가 된다(2026-09-28 발견, TossPaymentsApiClient.chargeWithBillingKey가
     * 429를 HttpClientErrorException.TooManyRequests로 감싸 던지는 경로 확인).
     */
    public boolean isBusinessDecline(ExternalApiException e) {
        return e.getCause() instanceof HttpClientErrorException httpEx
            && httpEx.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS;
    }
}
