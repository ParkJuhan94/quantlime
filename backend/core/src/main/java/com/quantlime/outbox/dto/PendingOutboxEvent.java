package com.quantlime.outbox.dto;

import com.quantlime.outbox.domain.OutboxEventType;

/**
 * 릴레이(event 모듈)에 넘기는 미발행 아웃박스 행 - 엔티티를 모듈 밖으로 내보내지 않는다(event 모듈은 JPA를 모르므로
 * 엔티티 타입이 시그니처에 보이기만 해도 javac가 경고를 낸다. 변환은 {@code OutboxService}가 한다).
 */
public record PendingOutboxEvent(Long id, OutboxEventType eventType, String aggregateId, String payload) {
}
