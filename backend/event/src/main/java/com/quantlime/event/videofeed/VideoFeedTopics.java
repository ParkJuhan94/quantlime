package com.quantlime.event.videofeed;

/** 영상 피드 파이프라인이 쓰는 Kafka 토픽 이름(2026-09-14 이벤트화). */
public final class VideoFeedTopics {

    public static final String VIDEO_SELECTED = "video.selected";
    public static final String VIDEO_TRANSCRIBED = "video.transcribed";

    private VideoFeedTopics() {
    }
}
