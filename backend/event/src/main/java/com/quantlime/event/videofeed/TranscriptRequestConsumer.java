package com.quantlime.event.videofeed;

import com.quantlime.videofeed.service.TranscriptProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * {@code video.selected} 토픽을 소비해 자막 조회를 트리거한다(2026-09-14).
 * blocking retry(DefaultErrorHandler+FixedBackOff)가 아니라 non-blocking
 * retry topic(@RetryableTopic)을 쓰는 이유 - quant-engine 호출의 read
 * timeout이 60초라(PythonEngineConfig), 컨슈머 스레드를 그대로 붙드는
 * blocking retry는 그 사이 다른 메시지 처리를 막아버린다.
 *
 * <p>{@code attempts="4"}(최초 1회 + 재시도 3회)는 기존 MAX_RETRY_COUNT=3을
 * 그대로 재현한 값. 지수 백오프 30s→90s→270s.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptRequestConsumer {

    private final TranscriptProcessingService transcriptProcessingService;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 30_000, multiplier = 3.0, maxDelay = 270_000))
    @KafkaListener(topics = VideoFeedTopics.VIDEO_SELECTED, groupId = "transcript-collector")
    public void onVideoSelected(VideoSelectedMessage message) {
        transcriptProcessingService.processVideo(message.videoId());
    }

    @DltHandler
    public void onDlt(VideoSelectedMessage message) {
        log.error("자막 수집 최종 실패(재시도 소진, DLT 이관) - Kafka UI에서 확인 후 필요시 수동 재발행할 것: videoId={}",
            message.videoId());
    }
}
