package com.quantlime.event.score;

import com.quantlime.event.envelope.DomainEventEnvelope;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code score.quadrant-alert.requested} 토픽의 payload - "thin event, thick lookup"
 * 원칙대로 userId만 담고, 컨슈머가 항상 DB에서 최신 스코어·옵트인 상태를 다시 읽는다.
 */
public record QuadrantAlertRequestedMessage(
    UUID eventId, Instant occurredAt, int version, Long userId) implements DomainEventEnvelope {

    public static QuadrantAlertRequestedMessage of(Long userId) {
        return new QuadrantAlertRequestedMessage(UUID.randomUUID(), Instant.now(), 1, userId);
    }
}
