package com.quantlime.event.videofeed;

/** {@code video.transcribed} 토픽의 Kafka payload - {@link VideoSelectedMessage}와 동일한 이유. */
public record VideoTranscribedMessage(Long videoId) {
}
