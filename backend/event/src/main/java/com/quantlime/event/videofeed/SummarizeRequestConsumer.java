package com.quantlime.event.videofeed;

import com.quantlime.videofeed.service.SummaryProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * {@code video.transcribed} 토픽을 소비해 AI 요약을 트리거한다(2026-09-14).
 * {@link TranscriptRequestConsumer}와 동일한 패턴/근거(non-blocking retry,
 * attempts=4, 30s→90s→270s 백오프). Gemini 일일 쿼터 게이트에 걸린 실패도
 * 똑같이 이 재시도→DLT 경로를 탄다 - "내일 자동 재시도" 같은 별도 처리는
 * 두지 않는다(계획 문서 참고).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummarizeRequestConsumer {

    private final SummaryProcessingService summaryProcessingService;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 30_000, multiplier = 3.0, maxDelay = 270_000))
    @KafkaListener(topics = VideoFeedTopics.VIDEO_TRANSCRIBED, groupId = "summary-collector")
    public void onVideoTranscribed(VideoTranscribedMessage message) {
        summaryProcessingService.processVideo(message.videoId());
    }

    @DltHandler
    public void onDlt(VideoTranscribedMessage message) {
        log.error("AI 요약 최종 실패(재시도 소진, DLT 이관) - Kafka UI에서 확인 후 필요시 수동 재발행할 것: videoId={}",
            message.videoId());
    }
}
