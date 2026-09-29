package com.quantlime.event.payment;

import com.quantlime.event.envelope.DomainEventEnvelope;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code payment.webhook.received} 토픽의 payload. {@code eventId}(봉투
 * 공통 필드, 발행마다 새로 발급)와 {@code payloadHash}(멱등 체크 기준,
 * 같은 웹훅 재전송이면 항상 동일)는 서로 다른 개념이다 - 전자는 이
 * 발행 자체의 추적용 식별자, 후자는 "같은 웹훅인가"를 판단하는 업무
 * 키다(PaymentWebhookReceivedEvent 주석 참고).
 */
public record PaymentWebhookReceivedMessage(
    UUID eventId, Instant occurredAt, int version, String payloadHash, String payload)
    implements DomainEventEnvelope {

    public static PaymentWebhookReceivedMessage of(String payloadHash, String payload) {
        return new PaymentWebhookReceivedMessage(UUID.randomUUID(), Instant.now(), 1, payloadHash, payload);
    }
}
