package com.quantlime.event.payment;

import com.quantlime.event.observability.KafkaDltNotifier;
import com.quantlime.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * {@code payment.webhook.received} 토픽을 소비해 서명 검증을 마친 토스페이먼츠
 * 웹훅을 실제 처리한다(2026-09-30, 카프카 다도메인 확장 Phase 3) - 수신
 * (컨트롤러, 서명 검증 후 즉시 200)과 처리(이 컨슈머)를 분리해, 처리 로직이
 * 향후 무거워져도 웹훅 수신 자체(Toss 재전송 회피)는 영향받지 않게 한다.
 * 다른 도메인과 동일한 non-blocking retry 패턴.
 *
 * <p>동시성을 지정하지 않아 기본값 1을 쓴다 - 결제 도메인이라
 * SubscriptionRenewalConsumer와 동일한 판단(단순한 순차 처리 선호).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWebhookConsumer {

    private final PaymentService paymentService;
    private final KafkaDltNotifier dltNotifier;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 30_000, multiplier = 3.0, maxDelay = 270_000))
    @KafkaListener(topics = PaymentTopics.PAYMENT_WEBHOOK_RECEIVED, groupId = "payment-webhook-collector")
    public void onWebhookReceived(PaymentWebhookReceivedMessage message) {
        paymentService.processWebhookEvent(message.payloadHash(), message.payload());
    }

    // exceptionMessage 헤더는 반드시 required=false여야 한다 -
    // SubscriptionRenewalConsumer.onDlt 주석 참고(2026-09-30 실제
    // 무한 재발행 루프 사고). try/catch로 이 핸들러 자신의 실패도
    // 절대 예외로 던지지 않는다(같은 사고 재발 방지).
    @DltHandler
    public void onDlt(PaymentWebhookReceivedMessage message,
        @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        try {
            String reason = exceptionMessage != null ? exceptionMessage : "사유 미상(DLT 예외 헤더 없음)";
            log.error("토스페이먼츠 웹훅 최종 처리 실패(재시도 소진, DLT 이관): payloadHash={}, error={}",
                message.payloadHash(), reason);
            dltNotifier.notify("payment-webhook", PaymentTopics.PAYMENT_WEBHOOK_RECEIVED,
                "payloadHash=" + message.payloadHash() + " - 웹훅 처리 최종 실패, 수동 확인 필요. error=" + reason);
        } catch (Exception e) {
            log.error("DLT 핸들러 자체 실패(무한 재발행 방지를 위해 예외를 삼킴): payloadHash={}",
                message.payloadHash(), e);
        }
    }
}
