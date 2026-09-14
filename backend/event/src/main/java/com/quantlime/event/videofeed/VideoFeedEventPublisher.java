package com.quantlime.event.videofeed;

import com.quantlime.videofeed.event.VideoSelectedEvent;
import com.quantlime.videofeed.event.VideoTranscribedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * core가 발행한 순수 도메인 이벤트(core는 Kafka를 모른다)를 받아 Kafka로
 * 중계한다. {@code AFTER_COMMIT}에서만 발행해, DB 트랜잭션이 롤백된 경우
 * Kafka에 먼저 이벤트가 나가버리는 dual-write 문제를 트랜잭셔널 아웃박스
 * 테이블 없이 막는다.
 *
 * <p>{@code fallbackExecution = true}가 필요한 이유: 기본값(false)이면
 * 활성 트랜잭션이 없을 때 이 리스너 자체가 조용히 스킵된다.
 * {@code TranscriptCollectionFacade.publishBacklog()}(기동 캐치업/관리자
 * 수동 트리거)는 트랜잭션 밖에서 이벤트를 발행하므로, 이 플래그가 없으면
 * 백로그 재발행이 전부 유실된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoFeedEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onVideoSelected(VideoSelectedEvent event) {
        String key = String.valueOf(event.videoId());
        kafkaTemplate.send(VideoFeedTopics.VIDEO_SELECTED, key, new VideoSelectedMessage(event.videoId()));
        log.debug("Kafka 발행: topic={}, videoId={}", VideoFeedTopics.VIDEO_SELECTED, event.videoId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onVideoTranscribed(VideoTranscribedEvent event) {
        String key = String.valueOf(event.videoId());
        kafkaTemplate.send(VideoFeedTopics.VIDEO_TRANSCRIBED, key, new VideoTranscribedMessage(event.videoId()));
        log.debug("Kafka 발행: topic={}, videoId={}", VideoFeedTopics.VIDEO_TRANSCRIBED, event.videoId());
    }
}
