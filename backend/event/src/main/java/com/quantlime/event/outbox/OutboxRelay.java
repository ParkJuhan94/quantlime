package com.quantlime.event.outbox;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.event.outbox.OutboxMessageRouter.OutboxRoute;
import com.quantlime.event.publish.KafkaEventSender;
import com.quantlime.event.publish.KafkaPublishException;
import com.quantlime.outbox.dto.OutboxBacklog;
import com.quantlime.outbox.dto.PendingOutboxEvent;
import com.quantlime.outbox.event.OutboxEventRecordedEvent;
import com.quantlime.outbox.service.OutboxService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 아웃박스 릴레이(2026-10-31) - "즉시 발행 + 폴링 보완" 방식.
 * <ul>
 *   <li>즉시: 아웃박스 행이 커밋되면 같은 스레드에서 바로 발행하고 브로커 확인(최대 3초)을 기다린다.
 *       평소 지연은 아웃박스 도입 전과 같다</li>
 *   <li>폴링: 1분마다 30초 넘게 남아 있는 미발행 행을 다시 보낸다. 즉시 발행이 실패했거나, 커밋과 발행 사이에
 *       서버가 죽은 경우를 메운다. 30초 하한은 진행 중인 즉시 발행과 같은 행을 동시에 보내지 않기 위해서다</li>
 * </ul>
 *
 * <p>브로커가 멈춘 동안 행마다 3초씩 기다리면 배치 스레드가 오래 묶이므로, 한 번 실패하면 1분 동안 즉시 발행을
 * 건너뛰고(행은 그대로 남아 폴링이 보냄) 폴링도 첫 실패에서 멈춘다. 발행은 at-least-once다 - 확인 응답 뒤
 * 상태 기록이 실패하면 같은 행이 다시 나갈 수 있고, 중복은 컨슈머가 막는다(구독 갱신은 결제 예정일 비교 + orderId 멱등).
 */
@Slf4j
@Component
public class OutboxRelay {

    static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(3);
    static final Duration POLL_MIN_AGE = Duration.ofSeconds(30);
    static final int POLL_BATCH_SIZE = 100;
    static final Duration IMMEDIATE_PAUSE_AFTER_FAILURE = Duration.ofMinutes(1);
    private static final String LOCK_KEY = "lock:outbox-relay";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final OutboxService outboxService;
    private final OutboxMessageRouter router;
    private final KafkaEventSender eventSender;
    private final RedisLockService redisLockService;
    private final Clock clock;
    private final AtomicLong pendingCount = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();
    private volatile Instant immediatePausedUntil = Instant.MIN;

    public OutboxRelay(OutboxService outboxService, OutboxMessageRouter router, KafkaEventSender eventSender,
        RedisLockService redisLockService, Clock clock, MeterRegistry meterRegistry) {
        this.outboxService = outboxService;
        this.router = router;
        this.eventSender = eventSender;
        this.redisLockService = redisLockService;
        this.clock = clock;
        // 폴링 주기(1분)마다 갱신하는 스냅샷 값이다 - 스크레이프마다 DB를 조회하지 않기 위해서.
        Gauge.builder("outbox.pending.events", pendingCount, AtomicLong::get)
            .description("아직 브로커로 보내지 못한 아웃박스 이벤트 수")
            .register(meterRegistry);
        Gauge.builder("outbox.pending.oldest.age", oldestPendingAgeSeconds, AtomicLong::get)
            .baseUnit("seconds")
            .description("가장 오래된 미발행 아웃박스 이벤트의 나이")
            .register(meterRegistry);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRecorded(OutboxEventRecordedEvent event) {
        if (Instant.now(clock).isBefore(immediatePausedUntil)) {
            log.debug("직전 발행 실패로 즉시 발행 보류(폴링이 보냄): outboxEventId={}", event.outboxEventId());
            return;
        }
        outboxService.findPending(event.outboxEventId()).ifPresent(this::publish);
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:60000}",
        initialDelayString = "${outbox.relay.initial-delay-ms:30000}")
    public void pollPending() {
        try {
            redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, this::relayBacklog);
        } catch (Exception e) {
            log.error("아웃박스 폴링 실패: reason={}", e.getMessage(), e);
        }
        refreshBacklogMetrics();
    }

    /** 미발행 행을 오래된 순으로 보낸다. 브로커 실패가 나면 거기서 멈춘다(나머지도 실패할 가능성이 높다). */
    int relayBacklog() {
        List<PendingOutboxEvent> targets = outboxService.findRelayTargets(POLL_MIN_AGE, POLL_BATCH_SIZE);
        int published = 0;
        for (PendingOutboxEvent target : targets) {
            if (!publish(target)) {
                break;
            }
            published++;
        }
        if (!targets.isEmpty()) {
            log.info("아웃박스 미발행 이벤트 재발행: 성공={}, 대상={}", published, targets.size());
        }
        return published;
    }

    /** @return 계속 진행해도 되면 true, 브로커 실패로 멈춰야 하면 false */
    private boolean publish(PendingOutboxEvent event) {
        OutboxRoute route;
        try {
            route = router.route(event);
        } catch (RuntimeException e) {
            // payload가 깨진 행은 재시도해도 같은 결과라 실패만 기록하고 다음 행으로 넘어간다(행은 남아 알림에 잡힌다).
            log.error("아웃박스 이벤트 변환 실패: outboxEventId={}, eventType={}", event.id(), event.eventType(), e);
            outboxService.recordFailure(event.id(), String.valueOf(e.getMessage()));
            return true;
        }
        try {
            eventSender.sendAndConfirm(route.topic(), route.key(), route.message(), CONFIRM_TIMEOUT);
        } catch (KafkaPublishException e) {
            immediatePausedUntil = Instant.now(clock).plus(IMMEDIATE_PAUSE_AFTER_FAILURE);
            outboxService.recordFailure(event.id(), String.valueOf(e.getMessage()));
            log.warn("아웃박스 이벤트 발행 실패(행이 남아 폴링이 다시 보냄): outboxEventId={}, topic={}",
                event.id(), route.topic());
            return false;
        }
        outboxService.markPublished(event.id());
        return true;
    }

    private void refreshBacklogMetrics() {
        try {
            OutboxBacklog backlog = outboxService.backlog();
            pendingCount.set(backlog.pendingCount());
            oldestPendingAgeSeconds.set(backlog.oldestPendingAgeSeconds());
        } catch (Exception e) {
            log.warn("아웃박스 적체 지표 갱신 실패: reason={}", e.getMessage());
        }
    }
}
