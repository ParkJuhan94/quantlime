package com.quantlime.event.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.event.publish.KafkaEventSender;
import com.quantlime.event.publish.KafkaPublishException;
import com.quantlime.event.subscription.SubscriptionRenewalDueMessage;
import com.quantlime.outbox.domain.OutboxEventType;
import com.quantlime.outbox.dto.OutboxBacklog;
import com.quantlime.outbox.dto.PendingOutboxEvent;
import com.quantlime.outbox.event.OutboxEventRecordedEvent;
import com.quantlime.outbox.service.OutboxService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final String TOPIC = "subscription.renewal.due";

    @Mock
    private OutboxService outboxService;

    @Mock
    private KafkaEventSender eventSender;

    @Mock
    private RedisLockService redisLockService;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-31T12:00:00Z"));
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        relay = new OutboxRelay(outboxService, new OutboxMessageRouter(objectMapper), eventSender,
            redisLockService, clock, registry);
    }

    private static PendingOutboxEvent renewalRow(long id, long subscriptionId) {
        return new PendingOutboxEvent(id, OutboxEventType.SUBSCRIPTION_RENEWAL_DUE, String.valueOf(subscriptionId),
            "{\"subscriptionId\":" + subscriptionId + ",\"billingDate\":\"2026-10-31\"}");
    }

    private static KafkaPublishException brokerDown() {
        return new KafkaPublishException(TOPIC, new RuntimeException("broker down"));
    }

    @SuppressWarnings("unchecked")
    private void lockAcquired() {
        given(redisLockService.runExclusively(eq("lock:outbox-relay"), any(Duration.class), any(Supplier.class)))
            .willAnswer(invocation -> Optional.of(((Supplier<Object>) invocation.getArgument(2)).get()));
    }

    @Test
    @DisplayName("[커밋 직후 즉시 발행에 성공하면 구독 ID를 키로 결제 예정일을 실어 보내고 발행 완료로 기록한다]")
    void onRecorded_success_publishesAndMarks() {
        // given
        given(outboxService.findPending(1L)).willReturn(Optional.of(renewalRow(1L, 42L)));

        // when
        relay.onRecorded(new OutboxEventRecordedEvent(1L));

        // then
        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(eventSender).sendAndConfirm(eq(TOPIC), eq("42"), message.capture(), eq(OutboxRelay.CONFIRM_TIMEOUT));
        SubscriptionRenewalDueMessage sent = (SubscriptionRenewalDueMessage) message.getValue();
        assertThat(sent.subscriptionId()).isEqualTo(42L);
        assertThat(sent.billingDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        verify(outboxService).markPublished(1L);
    }

    @Test
    @DisplayName("[즉시 발행이 실패하면 행을 발행 대기로 남기고, 1분 동안은 다음 즉시 발행을 건너뛴다(배치 스레드 보호)]")
    void onRecorded_failure_keepsPendingAndPausesImmediatePublishing() {
        // given
        given(outboxService.findPending(1L)).willReturn(Optional.of(renewalRow(1L, 42L)));
        willThrow(brokerDown()).given(eventSender).sendAndConfirm(anyString(), anyString(), any(), any());

        // when
        relay.onRecorded(new OutboxEventRecordedEvent(1L));
        relay.onRecorded(new OutboxEventRecordedEvent(2L));

        // then
        verify(outboxService).recordFailure(eq(1L), anyString());
        verify(outboxService, never()).markPublished(any());
        verify(outboxService, never()).findPending(2L);
    }

    @Test
    @DisplayName("[보류 1분이 지나면 즉시 발행을 다시 시도한다]")
    void onRecorded_afterPause_triesAgain() {
        // given
        given(outboxService.findPending(1L)).willReturn(Optional.of(renewalRow(1L, 42L)));
        given(outboxService.findPending(2L)).willReturn(Optional.of(renewalRow(2L, 43L)));
        willThrow(brokerDown()).given(eventSender).sendAndConfirm(eq(TOPIC), eq("42"), any(), any());
        relay.onRecorded(new OutboxEventRecordedEvent(1L));

        // when
        clock.advance(OutboxRelay.IMMEDIATE_PAUSE_AFTER_FAILURE.plusSeconds(1));
        relay.onRecorded(new OutboxEventRecordedEvent(2L));

        // then
        verify(outboxService).markPublished(2L);
    }

    @Test
    @DisplayName("[이미 발행된 행(즉시 발행과 폴링이 겹친 경우 등)은 다시 보내지 않는다]")
    void onRecorded_alreadyPublished_skips() {
        // given
        given(outboxService.findPending(1L)).willReturn(Optional.empty());

        // when
        relay.onRecorded(new OutboxEventRecordedEvent(1L));

        // then
        verify(eventSender, never()).sendAndConfirm(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("[폴링은 미발행 행을 오래된 순으로 보내고, 브로커 실패가 나면 그 자리에서 멈춘다]")
    void pollPending_stopsAtFirstBrokerFailure() {
        // given
        lockAcquired();
        given(outboxService.findRelayTargets(OutboxRelay.POLL_MIN_AGE, OutboxRelay.POLL_BATCH_SIZE))
            .willReturn(List.of(renewalRow(1L, 41L), renewalRow(2L, 42L), renewalRow(3L, 43L)));
        // 41은 성공, 42에서 브로커 실패 - 다른 인자 호출이 섞이므로 lenient로 둔다(strict stubs는 인자 불일치를 예외로 막는다).
        lenient().doThrow(brokerDown()).when(eventSender).sendAndConfirm(eq(TOPIC), eq("42"), any(), any());
        given(outboxService.backlog()).willReturn(new OutboxBacklog(2, 120));

        // when
        relay.pollPending();

        // then
        verify(outboxService).markPublished(1L);
        verify(outboxService).recordFailure(eq(2L), anyString());
        verify(eventSender, times(2)).sendAndConfirm(anyString(), anyString(), any(), any());
        assertThat(registry.get("outbox.pending.events").gauge().value()).isEqualTo(2);
        assertThat(registry.get("outbox.pending.oldest.age").gauge().value()).isEqualTo(120);
    }

    @Test
    @DisplayName("[payload가 깨진 행은 실패만 기록하고 다음 행 발행은 계속한다]")
    void pollPending_corruptPayload_continuesWithNextRow() {
        // given
        lockAcquired();
        PendingOutboxEvent corrupt = new PendingOutboxEvent(9L, OutboxEventType.SUBSCRIPTION_RENEWAL_DUE, "9", "not-json");
        given(outboxService.findRelayTargets(OutboxRelay.POLL_MIN_AGE, OutboxRelay.POLL_BATCH_SIZE))
            .willReturn(List.of(corrupt, renewalRow(10L, 44L)));
        given(outboxService.backlog()).willReturn(new OutboxBacklog(1, 60));

        // when
        relay.pollPending();

        // then
        verify(outboxService).recordFailure(eq(9L), anyString());
        verify(outboxService).markPublished(10L);
    }

    @Test
    @DisplayName("[다른 인스턴스가 폴링 중이면(락 미획득) 발행하지 않지만 적체 지표는 갱신한다]")
    @SuppressWarnings("unchecked")
    void pollPending_lockHeld_onlyRefreshesMetrics() {
        // given
        given(redisLockService.runExclusively(eq("lock:outbox-relay"), any(Duration.class), any(Supplier.class)))
            .willReturn(Optional.empty());
        given(outboxService.backlog()).willReturn(new OutboxBacklog(3, 30));

        // when
        relay.pollPending();

        // then
        verify(outboxService, never()).findRelayTargets(any(), anyInt());
        assertThat(registry.get("outbox.pending.events").gauge().value()).isEqualTo(3);
    }

    /** 즉시 발행 보류 시간(1분) 경과를 검증하려고 앞으로 감을 수 있게 한 시계. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
