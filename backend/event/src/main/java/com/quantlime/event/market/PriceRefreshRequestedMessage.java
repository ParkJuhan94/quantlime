package com.quantlime.event.market;

import com.quantlime.event.envelope.DomainEventEnvelope;
import com.quantlime.market.event.PriceRefreshRequestedEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code price.refresh.requested} 토픽의 Kafka payload. videofeed의 "thin
 * event"와 달리 {@code latestScoreDate}까지 실어 보낸다 - 컨슈머가 종목마다
 * 다시 조회하면 2026-09 성능 감사에서 없앤 9,000회 개별 쿼리가 재발하기
 * 때문(발행 시점에 배치 스냅샷으로 이미 알고 있는 값을 그대로 전달).
 */
public record PriceRefreshRequestedMessage(
    UUID eventId, Instant occurredAt, int version,
    String runId, String stockCode, String peerGroup, LocalDate latestScoreDate)
    implements DomainEventEnvelope {

    public static PriceRefreshRequestedMessage of(PriceRefreshRequestedEvent event) {
        return new PriceRefreshRequestedMessage(UUID.randomUUID(), Instant.now(), 1,
            event.runId(), event.stockCode(), event.peerGroup().getWireValue(), event.latestScoreDate());
    }
}
