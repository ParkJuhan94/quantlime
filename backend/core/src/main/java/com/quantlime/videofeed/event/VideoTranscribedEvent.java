package com.quantlime.videofeed.event;

/** {@link VideoSelectedEvent}와 동일한 이유로 core 전용 순수 도메인 이벤트다. */
public record VideoTranscribedEvent(Long videoId) {
}
