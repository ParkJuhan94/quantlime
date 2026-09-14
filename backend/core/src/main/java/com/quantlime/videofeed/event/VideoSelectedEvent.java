package com.quantlime.videofeed.event;

/**
 * {@code core} 안에서만 도는 순수 도메인 이벤트 - Kafka를 전혀 모른다.
 * {@code event} 모듈의 {@code VideoFeedEventPublisher}가 이걸 받아 Kafka
 * 토픽으로 중계한다({@code event → core} 단방향 의존이라 core는 반대로
 * Kafka를 참조할 수 없음).
 */
public record VideoSelectedEvent(Long videoId) {
}
