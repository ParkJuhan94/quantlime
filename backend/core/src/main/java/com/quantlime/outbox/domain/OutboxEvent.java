package com.quantlime.outbox.domain;

import static lombok.AccessLevel.PROTECTED;

import com.quantlime.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 트랜잭셔널 아웃박스(2026-10-31). 발행해야 할 이벤트를 먼저 DB에 남기고, event 모듈의 릴레이가 브로커가 받았음을
 * 확인한 뒤에만 {@link OutboxStatus#PUBLISHED}로 바꾼다 - 브로커가 멈춰 있어도 이벤트가 행으로 남아 복구 뒤 다시 나간다.
 *
 * <p>{@code dedupKey}는 같은 업무 사건이 두 번 적재되는 걸 막는다(예: 구독 갱신은 "구독 ID + 결제 예정일" - 스케줄러가
 * 같은 날 두 번 돌아도 한 행). 발행 자체는 at-least-once라 중복 전달은 컨슈머 멱등이 막는다.
 */
@Entity
@Table(name = "outbox_event",
    uniqueConstraints = @UniqueConstraint(name = "uk_outbox_event_dedup_key", columnNames = "dedup_key"),
    // 릴레이 폴링(status=PENDING, id 오름차순)과 정리(status=PUBLISHED, published_at 기준)가 모두 status로 시작한다.
    indexes = @Index(name = "idx_outbox_event_status", columnList = "status, outbox_event_id"))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class OutboxEvent extends TimeBaseEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_event_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private OutboxEventType eventType;

    // 파티션 키로도 쓴다(같은 집계의 이벤트가 같은 파티션으로 가 순서가 유지되도록).
    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "dedup_key", nullable = false, length = 191)
    private String dedupKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private @Nullable String lastError;

    @Column(name = "published_at")
    private @Nullable LocalDateTime publishedAt;

    @Builder
    private OutboxEvent(OutboxEventType eventType, String aggregateId, String dedupKey, String payload) {
        Assert.notNull(eventType, "이벤트 종류는 필수입니다.");
        Assert.hasText(aggregateId, "집계 ID는 필수입니다.");
        Assert.hasText(dedupKey, "중복 방지 키는 필수입니다.");
        Assert.hasText(payload, "payload는 필수입니다.");
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.dedupKey = dedupKey;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attemptCount = 0;
    }

    public static OutboxEvent of(OutboxEventType eventType, String aggregateId, String dedupKey, String payload) {
        return OutboxEvent.builder()
            .eventType(eventType)
            .aggregateId(aggregateId)
            .dedupKey(dedupKey)
            .payload(payload)
            .build();
    }

    public boolean isPending() {
        return status == OutboxStatus.PENDING;
    }

    public void markPublished(LocalDateTime now) {
        this.status = OutboxStatus.PUBLISHED;
        this.attemptCount++;
        this.publishedAt = now;
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attemptCount++;
        this.lastError = error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }
}
