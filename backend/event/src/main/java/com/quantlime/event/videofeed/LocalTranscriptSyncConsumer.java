package com.quantlime.event.videofeed;

import com.quantlime.videofeed.service.LocalTranscriptSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code video.transcribed} 토픽을 소비해, 로컬에서 새로 저장된 자막을
 * 운영 서버로 실시간 동기화한다(2026-09-15). SummarizeRequestConsumer와
 * 같은 토픽을 각자 다른 컨슈머 그룹({@code local-prod-sync})으로 구독하므로
 * 서로 간섭하지 않는다.
 *
 * <p>{@code @Profile("dev")}로 로컬(기본 프로파일)에서만 활성화한다 - 운영
 * 프로파일에서까지 이 컨슈머가 뜨면 "운영이 자기 자신에게 자막을 동기화
 * 시도"하는 무의미한 상황이 된다. 실패해도 재시도 토픽(@RetryableTopic)을
 * 쓰지 않는다 - LocalTranscriptSyncService 자체가 예외를 삼키고 로그만
 * 남기는 best-effort 경로이기 때문(놓친 건 sync-transcripts-to-prod.sh가
 * 나중에 보충).
 */
@Component
@RequiredArgsConstructor
@Profile("dev")
public class LocalTranscriptSyncConsumer {

    private final LocalTranscriptSyncService localTranscriptSyncService;

    @KafkaListener(topics = VideoFeedTopics.VIDEO_TRANSCRIBED, groupId = "local-prod-sync")
    public void onVideoTranscribed(VideoTranscribedMessage message) {
        localTranscriptSyncService.syncOne(message.videoId());
    }
}
