package com.quantlime.notification.event;

/**
 * 사분면 변화 알림 판정을 사용자 한 명 단위로 요청하는 순수 도메인 이벤트 - core는
 * Kafka를 모른다({@code event.score.ScoreEventPublisher}가 Kafka로 중계).
 * 사용자 단위로 쪼개 한 명의 실패(FCM 오류 등)가 다른 사용자 알림을 막지 않게 한다.
 */
public record QuadrantAlertRequestedEvent(Long userId) {
}
