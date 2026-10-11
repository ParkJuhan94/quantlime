package com.quantlime.event.score;

import com.quantlime.event.observability.KafkaDltNotifier;
import com.quantlime.event.retry.RetryBackoff;
import com.quantlime.notification.service.QuadrantChangeAlertService;
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
 * {@code score.quadrant-alert.requested} 토픽을 소비해 사용자 한 명의 사분면 변화 알림을
 * 판정·발송한다. 다른 컨슈머와 같은 non-blocking retry 패턴이다. 재전달·재시도가 와도
 * 중복 통지되지 않는 건 {@code QuadrantAlertSentStore}의 (사용자, 종목, 산출일) 장부가 보장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuadrantAlertConsumer {

    private final QuadrantChangeAlertService quadrantChangeAlertService;
    private final KafkaDltNotifier dltNotifier;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delayExpression = RetryBackoff.DELAY_MS, multiplierExpression = RetryBackoff.MULTIPLIER,
        maxDelayExpression = RetryBackoff.MAX_DELAY_MS))
    @KafkaListener(topics = ScoreTopics.QUADRANT_ALERT_REQUESTED, groupId = "quadrant-alert-notifier")
    public void onQuadrantAlertRequested(QuadrantAlertRequestedMessage message) {
        quadrantChangeAlertService.notifyUser(message.userId());
    }

    // DltHandler는 절대 예외를 던지지 않는다 - 던지면 같은 DLT 토픽에 재발행돼 루프가 된다
    // (SubscriptionRenewalConsumer 주석 참고). 헤더도 required=false.
    @DltHandler
    public void onDlt(QuadrantAlertRequestedMessage message,
        @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        try {
            String reason = exceptionMessage != null ? exceptionMessage : "사유 미상(DLT 예외 헤더 없음)";
            log.error("사분면 변화 알림 재시도 소진: userId={}, error={}", message.userId(), reason);
            dltNotifier.notify("score-quadrant-alert", ScoreTopics.QUADRANT_ALERT_REQUESTED,
                "userId=" + message.userId() + " - 사분면 변화 알림 재시도 소진. error=" + reason);
        } catch (Exception e) {
            log.error("DLT 핸들러 자체 실패(무한 재발행 방지를 위해 예외를 삼킴): userId={}", message.userId(), e);
        }
    }
}
