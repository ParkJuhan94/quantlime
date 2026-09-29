package com.quantlime.event.payment;

import com.quantlime.payment.event.PaymentWebhookReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
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

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWebhookReceived(PaymentWebhookReceivedEvent event) {
        kafkaTemplate.send(PaymentTopics.PAYMENT_WEBHOOK_RECEIVED, event.payloadHash(),
            PaymentWebhookReceivedMessage.of(event.payloadHash(), event.payload()));
    }
}
