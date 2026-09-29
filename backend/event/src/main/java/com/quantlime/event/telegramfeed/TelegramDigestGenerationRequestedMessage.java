package com.quantlime.event.telegramfeed;

import com.quantlime.event.envelope.DomainEventEnvelope;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code telegram.digest.generation.requested} 토픽의 payload - videofeed와
 * 동일한 "thin event, thick lookup" 원칙(channelId+date만 담고, 컨슈머는
 * 항상 DB에서 최신 상태를 다시 읽는다).
 */
public record TelegramDigestGenerationRequestedMessage(
    UUID eventId, Instant occurredAt, int version, Long channelId, LocalDate date)
    implements DomainEventEnvelope {

    public static TelegramDigestGenerationRequestedMessage of(Long channelId, LocalDate date) {
        return new TelegramDigestGenerationRequestedMessage(UUID.randomUUID(), Instant.now(), 1, channelId, date);
    }
}
