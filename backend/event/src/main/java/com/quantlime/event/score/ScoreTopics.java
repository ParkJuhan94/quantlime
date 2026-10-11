package com.quantlime.event.score;

/** 스코어 기반 후속 작업(사분면 변화 알림)이 쓰는 Kafka 토픽 이름. */
public final class ScoreTopics {

    public static final String QUADRANT_ALERT_REQUESTED = "score.quadrant-alert.requested";

    private ScoreTopics() {
    }
}
