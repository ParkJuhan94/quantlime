package com.quantlime.event.videofeed;

/**
 * {@code video.selected} 토픽의 실제 Kafka payload("thin event, thick lookup" -
 * videoId만 담고, 소비자는 항상 DB에서 최신 상태를 다시 읽는다).
 */
public record VideoSelectedMessage(Long videoId) {
}
