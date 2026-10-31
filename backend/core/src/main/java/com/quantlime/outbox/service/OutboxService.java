package com.quantlime.outbox.service;

import com.quantlime.outbox.domain.OutboxEvent;
import com.quantlime.outbox.domain.OutboxStatus;
import com.quantlime.outbox.dto.OutboxBacklog;
import com.quantlime.outbox.dto.PendingOutboxEvent;
import com.quantlime.outbox.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * event 모듈의 아웃박스 릴레이가 쓰는 상태 전이 창구. 발행(브로커 왕복)은 트랜잭션 밖에서 하고, 그 결과만 짧은
 * 트랜잭션으로 기록한다 - 발행을 기다리는 동안 DB 커넥션을 물고 있지 않기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<PendingOutboxEvent> findPending(Long outboxEventId) {
        return outboxEventRepository.findById(outboxEventId).filter(OutboxEvent::isPending).map(OutboxService::toPending);
    }

    @Transactional(readOnly = true)
    public List<PendingOutboxEvent> findRelayTargets(Duration minAge, int limit) {
        LocalDateTime createdBefore = LocalDateTime.now(clock).minus(minAge);
        return outboxEventRepository.findRelayTargets(OutboxStatus.PENDING, createdBefore, PageRequest.of(0, limit))
            .stream()
            .map(OutboxService::toPending)
            .toList();
    }

    // REQUIRES_NEW: 즉시 발행은 AFTER_COMMIT 리스너 안에서 돈다. 그 시점엔 끝난 트랜잭션의 리소스가 아직 바인딩돼 있어
    // REQUIRED로 열면 그 트랜잭션에 합류해 변경이 커밋되지 않는다 - 그러면 발행된 행이 PENDING으로 남아 폴링이 다시 보낸다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(Long outboxEventId) {
        outboxEventRepository.findById(outboxEventId)
            .filter(OutboxEvent::isPending)
            .ifPresent(event -> event.markPublished(LocalDateTime.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long outboxEventId, String error) {
        outboxEventRepository.findById(outboxEventId)
            .filter(OutboxEvent::isPending)
            .ifPresent(event -> event.recordFailure(error));
    }

    @Transactional(readOnly = true)
    public OutboxBacklog backlog() {
        long pending = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        long oldestAgeSeconds = outboxEventRepository.findFirstByStatusOrderByIdAsc(OutboxStatus.PENDING)
            .map(event -> Math.max(0, Duration.between(event.getCreatedAt(), LocalDateTime.now(clock)).toSeconds()))
            .orElse(0L);
        return new OutboxBacklog(pending, oldestAgeSeconds);
    }

    private static PendingOutboxEvent toPending(OutboxEvent event) {
        return new PendingOutboxEvent(event.getId(), event.getEventType(), event.getAggregateId(), event.getPayload());
    }

    @Transactional
    public int purgePublishedBefore(LocalDateTime cutoff) {
        int deleted = outboxEventRepository.deleteByStatusAndPublishedAtBefore(OutboxStatus.PUBLISHED, cutoff);
        log.info("발행 완료된 아웃박스 이벤트 정리: cutoff={}, deleted={}", cutoff, deleted);
        return deleted;
    }
}
