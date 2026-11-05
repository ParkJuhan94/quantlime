package com.quantlime.event.subscription;

import com.quantlime.event.envelope.DomainEventEnvelope;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * {@code subscription.renewal.due} 토픽의 payload - videofeed와 동일한
 * "thin event, thick lookup" 원칙(subscriptionId만 담고, 컨슈머는 항상 DB에서
 * 최신 상태를 다시 읽는다).
 */
// billingDate(2026-10-31 추가)는 요청이 가리키는 결제 예정일 - 아웃박스가 늦게 보낸 요청이나 중복 전달을 컨슈머가
// 거르는 기준이다. 필드 추가 전에 발행된 메시지는 이 값이 null로 역직렬화된다(FAIL_ON_UNKNOWN_PROPERTIES 비활성).
public record SubscriptionRenewalDueMessage(
    UUID eventId, Instant occurredAt, int version, Long subscriptionId, @Nullable LocalDate billingDate)
    implements DomainEventEnvelope {

    public static SubscriptionRenewalDueMessage of(Long subscriptionId) {
        return of(subscriptionId, null);
    }

    public static SubscriptionRenewalDueMessage of(Long subscriptionId, @Nullable LocalDate billingDate) {
        return new SubscriptionRenewalDueMessage(UUID.randomUUID(), Instant.now(), 1, subscriptionId, billingDate);
    }
}
