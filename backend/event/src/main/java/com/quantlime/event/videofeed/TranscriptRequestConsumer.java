package com.quantlime.event.videofeed;

import com.quantlime.event.observability.KafkaDltNotifier;
import com.quantlime.event.retry.RetryBackoff;
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
    private final KafkaDltNotifier dltNotifier;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delayExpression = RetryBackoff.DELAY_MS, multiplierExpression = RetryBackoff.MULTIPLIER,
        maxDelayExpression = RetryBackoff.MAX_DELAY_MS))
    @KafkaListener(topics = VideoFeedTopics.VIDEO_SELECTED, groupId = "transcript-collector")
    public void onVideoSelected(VideoSelectedMessage message) {
        transcriptProcessingService.processVideo(message.videoId());
    }

    // 이 메서드는 절대 예외를 던지면 안 된다(2026-09-30, SubscriptionRenewalConsumer에서
    // 실제로 겪은 사고) - @DltHandler가 예외를 던지면 Spring Kafka가 "더 갈 곳이
    // 없다"며 같은 DLT 토픽에 다시 발행해, 그 재발행이 또 실패하면 무한
    // 재발행 루프가 된다(로컬 실측 - 초당 100개 이상 증식). try/catch로 이
    // 핸들러 자체를 최종 방어선으로 만든다.
    @DltHandler
    public void onDlt(VideoSelectedMessage message) {
        try {
            log.error("자막 수집 최종 실패(재시도 소진, DLT 이관) - Kafka UI에서 확인 후 필요시 수동 재발행할 것: videoId={}",
                message.videoId());
            dltNotifier.notify("videofeed-transcript", VideoFeedTopics.VIDEO_SELECTED,
                "videoId=" + message.videoId() + " - 자막 수집 최종 실패, 수동 재발행 필요"
                    + "(POST /api/admin/feed/transcribe)");
        } catch (Exception e) {
            log.error("DLT 핸들러 자체 실패(무한 재발행 방지를 위해 예외를 삼킴): videoId={}",
                message.videoId(), e);
        }
    }
}
