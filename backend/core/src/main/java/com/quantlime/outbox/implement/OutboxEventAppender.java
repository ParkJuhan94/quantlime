package com.quantlime.outbox.implement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.outbox.domain.OutboxEvent;
import com.quantlime.outbox.domain.OutboxEventType;
import com.quantlime.outbox.event.OutboxEventRecordedEvent;
import com.quantlime.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아웃박스 행을 적재한다. 호출자의 트랜잭션에 합류하므로(없으면 새로 연다) 업무 변경과 같은 커밋에 묶인다.
 * 커밋 뒤 즉시 발행을 시도하도록 {@link OutboxEventRecordedEvent}를 함께 낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventAppender {

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /** 새로 적재했으면 true, 같은 {@code dedupKey}가 이미 있으면 false(중복 적재 생략)를 돌려준다. */
    @Transactional
    public boolean append(OutboxEventType eventType, String aggregateId, String dedupKey, Object payload) {
        if (outboxEventRepository.existsByDedupKey(dedupKey)) {
            log.info("이미 적재된 아웃박스 이벤트, 생략: eventType={}, dedupKey={}", eventType, dedupKey);
            return false;
        }
        OutboxEvent saved = outboxEventRepository.save(
            OutboxEvent.of(eventType, aggregateId, dedupKey, toJson(payload)));
        eventPublisher.publishEvent(new OutboxEventRecordedEvent(saved.getId()));
        return true;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("아웃박스 payload 직렬화에 실패했습니다.", e);
        }
    }
}
