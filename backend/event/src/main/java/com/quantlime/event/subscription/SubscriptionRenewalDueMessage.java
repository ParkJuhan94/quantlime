package com.quantlime.event.subscription;

import com.quantlime.event.envelope.DomainEventEnvelope;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code subscription.renewal.due} 토픽의 payload - videofeed와 동일한
 * "thin event, thick lookup" 원칙(subscriptionId만 담고, 컨슈머는 항상 DB에서
 * 최신 상태를 다시 읽는다).
 */
public record SubscriptionRenewalDueMessage(
    UUID eventId, Instant occurredAt, int version, Long subscriptionId) implements DomainEventEnvelope {

    public static SubscriptionRenewalDueMessage of(Long subscriptionId) {
        return new SubscriptionRenewalDueMessage(UUID.randomUUID(), Instant.now(), 1, subscriptionId);
    }
}
