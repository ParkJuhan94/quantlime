package com.quantlime.event.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.event.subscription.SubscriptionRenewalDueMessage;
import com.quantlime.event.subscription.SubscriptionTopics;
import com.quantlime.outbox.dto.PendingOutboxEvent;
import com.quantlime.subscription.event.SubscriptionRenewalDueEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 아웃박스 행(core의 도메인 payload)을 Kafka 토픽·메시지로 바꾼다. core는 Kafka를 모르므로 이 매핑은 event 모듈이
 * 소유한다. 종류별 switch라 {@code OutboxEventType}에 값을 추가하면 여기서 컴파일 에러가 나 매핑 누락을 막는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxMessageRouter {

    private final ObjectMapper objectMapper;

    public OutboxRoute route(PendingOutboxEvent event) {
        return switch (event.eventType()) {
            case SUBSCRIPTION_RENEWAL_DUE -> {
                SubscriptionRenewalDueEvent payload = read(event, SubscriptionRenewalDueEvent.class);
                yield new OutboxRoute(SubscriptionTopics.SUBSCRIPTION_RENEWAL_DUE, event.aggregateId(),
                    SubscriptionRenewalDueMessage.of(payload.subscriptionId(), payload.billingDate()));
            }
        };
    }

    private <T> T read(PendingOutboxEvent event, Class<T> type) {
        try {
            return objectMapper.readValue(event.payload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("아웃박스 payload 역직렬화 실패: outboxEventId=" + event.id(), e);
        }
    }

    public record OutboxRoute(String topic, String key, Object message) {
    }
}
