package com.quantlime.event.videofeed;

import com.quantlime.event.envelope.DomainEventEnvelope;
import java.time.Instant;
import java.util.UUID;

/** {@code video.transcribed} 토픽의 Kafka payload - {@link VideoSelectedMessage}와 동일한 이유. */
public record VideoTranscribedMessage(
    UUID eventId, Instant occurredAt, int version, Long videoId) implements DomainEventEnvelope {

    public static VideoTranscribedMessage of(Long videoId) {
        return new VideoTranscribedMessage(UUID.randomUUID(), Instant.now(), 1, videoId);
    }
}
