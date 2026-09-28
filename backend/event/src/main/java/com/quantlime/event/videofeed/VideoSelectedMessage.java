package com.quantlime.event.videofeed;

import com.quantlime.event.envelope.DomainEventEnvelope;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code video.selected} 토픽의 실제 Kafka payload("thin event, thick lookup" -
 * videoId만 담고, 소비자는 항상 DB에서 최신 상태를 다시 읽는다).
 */
public record VideoSelectedMessage(
    UUID eventId, Instant occurredAt, int version, Long videoId) implements DomainEventEnvelope {

    public static VideoSelectedMessage of(Long videoId) {
        return new VideoSelectedMessage(UUID.randomUUID(), Instant.now(), 1, videoId);
    }
}
