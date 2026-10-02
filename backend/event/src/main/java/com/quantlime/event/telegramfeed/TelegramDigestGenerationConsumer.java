package com.quantlime.event.telegramfeed;

import com.quantlime.event.observability.KafkaDltNotifier;
import com.quantlime.event.retry.RetryBackoff;
import com.quantlime.telegramfeed.service.TelegramDigestGenerationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * {@code telegram.digest.generation.requested} 토픽을 소비해 채널 하나의
 * 다이제스트 생성을 처리한다(2026-09-30, 카프카 다도메인 확장 Phase 4).
 * 다른 도메인과 동일한 non-blocking retry 패턴 - 채널 하나가 Gemini 호출
 * 실패 등으로 DLT까지 가도 다른 채널 처리에 영향을 주지 않는다(예전 동기
 * 루프의 try/catch 격리를 Kafka 재시도/DLT 격리로 대체).
 *
 * <p>동시성을 지정하지 않아 기본값 1을 쓴다 - 채널 수가 적고(수 개),
 * Gemini 무료 티어 쿼터를 유튜브와 공유하므로 동시 호출로 얻을 이점보다
 * 순차 처리로 쿼터 소모를 예측 가능하게 유지하는 쪽을 택했다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramDigestGenerationConsumer {

    private final TelegramDigestGenerationFacade telegramDigestGenerationFacade;
    private final KafkaDltNotifier dltNotifier;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delayExpression = RetryBackoff.DELAY_MS, multiplierExpression = RetryBackoff.MULTIPLIER,
        maxDelayExpression = RetryBackoff.MAX_DELAY_MS))
    @KafkaListener(topics = TelegramFeedTopics.TELEGRAM_DIGEST_GENERATION_REQUESTED,
        groupId = "telegram-digest-collector")
    public void onDigestGenerationRequested(TelegramDigestGenerationRequestedMessage message) {
        telegramDigestGenerationFacade.generateForChannel(message.channelId(), message.date());
    }

    // exceptionMessage 헤더는 반드시 required=false여야 한다 -
    // SubscriptionRenewalConsumer.onDlt 주석 참고(2026-09-30 실제
    // 무한 재발행 루프 사고). try/catch로 이 핸들러 자신의 실패도
    // 절대 예외로 던지지 않는다(같은 사고 재발 방지).
    @DltHandler
    public void onDlt(TelegramDigestGenerationRequestedMessage message,
        @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        try {
            String reason = exceptionMessage != null ? exceptionMessage : "사유 미상(DLT 예외 헤더 없음)";
            log.error("텔레그램 다이제스트 생성 최종 실패(재시도 소진, DLT 이관) - 다음 정기 배치"
                    + "(08:30/13:30/20:30)에서 자동 재시도됨: channelId={}, date={}, error={}",
                message.channelId(), message.date(), reason);
            dltNotifier.notify("telegram-digest", TelegramFeedTopics.TELEGRAM_DIGEST_GENERATION_REQUESTED,
                "channelId=" + message.channelId() + ", date=" + message.date()
                    + " - 다이제스트 생성 최종 실패. 다음 정기 배치에서 자동 재시도됩니다. error=" + reason);
        } catch (Exception e) {
            log.error("DLT 핸들러 자체 실패(무한 재발행 방지를 위해 예외를 삼킴): channelId={}, date={}",
                message.channelId(), message.date(), e);
        }
    }
}
