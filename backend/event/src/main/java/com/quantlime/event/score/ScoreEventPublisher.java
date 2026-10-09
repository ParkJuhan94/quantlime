package com.quantlime.event.score;

import com.quantlime.event.publish.KafkaEventSender;
import com.quantlime.notification.event.QuadrantAlertRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * core(QuadrantAlertDispatcher)가 발행한 순수 도메인 이벤트를 Kafka로 중계한다 -
 * market/subscription과 동일한 원칙. {@code fallbackExecution = true}가 필요한 이유도
 * 동일 - 발행 지점이 트랜잭션 밖(배치 오케스트레이션)이라 기본값이면 리스너가 스킵된다.
 */
@Component
@RequiredArgsConstructor
public class ScoreEventPublisher {

    private final KafkaEventSender eventSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQuadrantAlertRequested(QuadrantAlertRequestedEvent event) {
        eventSender.send(ScoreTopics.QUADRANT_ALERT_REQUESTED, String.valueOf(event.userId()),
            QuadrantAlertRequestedMessage.of(event.userId()));
    }
}
