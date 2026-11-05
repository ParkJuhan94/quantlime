package com.quantlime.outbox.event;

/**
 * 아웃박스 행이 커밋됐다는 순수 도메인 이벤트 - event 모듈의 릴레이가 커밋 직후 바로 발행을 시도한다
 * ("즉시 발행 + 폴링 보완"의 즉시 쪽). 이 시도가 실패하거나 서버가 그 사이 죽어도 행이 남아 폴링이 다시 보낸다.
 */
public record OutboxEventRecordedEvent(Long outboxEventId) {
}
