package com.quantlime.event.payment;

import com.quantlime.event.publish.KafkaEventSender;
import com.quantlime.payment.event.PaymentWebhookReceivedEvent;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * core(PaymentService.handleWebhook)가 발행한 순수 도메인 이벤트를 Kafka로
 * 중계한다 - videofeed/market/subscription과 동일한 원칙. {@code
 * fallbackExecution = true}가 필요한 이유도 동일 - 웹훅 수신 자체가 DB
 * 트랜잭션 안에서 일어나지 않는다(서명 검증만 하고 바로 발행).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    // 웹훅 응답이 늘어지지 않게 짧게 - 이 안에 브로커 확인이 안 되면 5xx로 응답해 재전송을 유도한다.
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(3);

    private final KafkaEventSender eventSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWebhookReceived(PaymentWebhookReceivedEvent event) {
        // 다른 도메인과 달리 동기로 발행을 확인한다(2026-10-01) - 서명 검증을 마친 웹훅은 컨트롤러가
        // 곧바로 200을 돌려줘 Toss 재전송이 멈추므로, 발행이 유실되면 복구할 길이 없다. 실패하면 예외가
        // 컨트롤러까지 전파돼 5xx로 응답하고 Toss가 재전송한다(중복은 컨슈머가 payload 해시로 걸러낸다).
        eventSender.sendAndConfirm(PaymentTopics.PAYMENT_WEBHOOK_RECEIVED, event.payloadHash(),
            PaymentWebhookReceivedMessage.of(event.payloadHash(), event.payload()), CONFIRM_TIMEOUT);
    }
}
