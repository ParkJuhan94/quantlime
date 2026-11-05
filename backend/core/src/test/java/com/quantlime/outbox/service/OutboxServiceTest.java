package com.quantlime.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.quantlime.common.config.JpaAuditingConfig;
import com.quantlime.outbox.domain.OutboxEvent;
import com.quantlime.outbox.domain.OutboxEventType;
import com.quantlime.outbox.domain.OutboxStatus;
import com.quantlime.outbox.dto.OutboxBacklog;
import com.quantlime.outbox.dto.PendingOutboxEvent;
import com.quantlime.outbox.implement.OutboxEventAppender;
import com.quantlime.outbox.repository.OutboxEventRepository;
import com.quantlime.support.DataJpaTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 실제 커밋 경계(즉시 발행은 AFTER_COMMIT)와 감사 컬럼(createdAt) 기준 조회를 검증하려고 앰비언트 트랜잭션을 끈다.
@Tag("integration")
@Import({OutboxService.class, OutboxEventAppender.class, JpaAuditingConfig.class, OutboxServiceTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxServiceTest extends DataJpaTestSupport {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxEventAppender outboxEventAppender;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @TestConfiguration
    static class Config {

        // 감사 컬럼(createdAt)은 JVM 기본 타임존으로 찍히므로 같은 기준의 시계를 쓴다(TZ=UTC인 CI에서도 맞도록).
        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }

        // 운영(Spring Boot 기본 ObjectMapper)과 같이 날짜를 ISO 문자열로 쓴다.
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }
    }

    @AfterEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
    }

    private boolean appendRenewal(long subscriptionId) {
        return outboxEventAppender.append(OutboxEventType.SUBSCRIPTION_RENEWAL_DUE, String.valueOf(subscriptionId),
            "SUBSCRIPTION_RENEWAL_DUE:" + subscriptionId + ":2026-10-31",
            Map.of("subscriptionId", subscriptionId, "billingDate", LocalDate.of(2026, 10, 31)));
    }

    @Test
    @DisplayName("[같은 중복 방지 키(구독+결제 예정일)는 한 번만 적재되고 payload는 JSON으로 저장된다]")
    void append_sameDedupKey_storedOnce() {
        // given & when
        boolean first = appendRenewal(42L);
        boolean second = appendRenewal(42L);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        List<OutboxEvent> rows = outboxEventRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(rows.get(0).getPayload()).contains("\"subscriptionId\":42").contains("\"billingDate\":\"2026-10-31\"");
    }

    @Test
    @DisplayName("[폴링 대상은 최소 나이를 넘긴 미발행 행만, 오래된 순으로 고른다]")
    void findRelayTargets_onlyPendingOlderThanMinAge() {
        // given
        appendRenewal(1L);
        appendRenewal(2L);
        appendRenewal(3L);
        Long publishedId = outboxEventRepository.findAll().get(1).getId();
        outboxService.markPublished(publishedId);

        // when
        List<PendingOutboxEvent> fresh = outboxService.findRelayTargets(Duration.ofHours(1), 100);
        List<PendingOutboxEvent> due = outboxService.findRelayTargets(Duration.ZERO.minusSeconds(1), 100);

        // then
        assertThat(fresh).isEmpty();
        assertThat(due).extracting(PendingOutboxEvent::aggregateId).containsExactly("1", "3");
    }

    @Test
    @DisplayName("[발행 실패는 시도 횟수와 사유만 남기고 발행 대기를 유지하며, 적체 지표에 잡힌다]")
    void recordFailure_keepsPending_andCountsInBacklog() {
        // given
        appendRenewal(7L);
        Long id = outboxEventRepository.findAll().get(0).getId();

        // when
        outboxService.recordFailure(id, "broker down");
        OutboxBacklog backlog = outboxService.backlog();

        // then
        OutboxEvent row = outboxEventRepository.findById(id).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(row.getLastError()).isEqualTo("broker down");
        assertThat(backlog.pendingCount()).isEqualTo(1);
        assertThat(outboxService.findPending(id)).isPresent();
    }

    @Test
    @DisplayName("[정리는 기준 시각 이전에 발행 완료된 행만 지우고, 미발행 행은 남긴다]")
    void purgePublishedBefore_deletesOnlyPublished() {
        // given
        appendRenewal(1L);
        appendRenewal(2L);
        Long publishedId = outboxEventRepository.findAll().get(0).getId();
        outboxService.markPublished(publishedId);

        // when
        int deleted = outboxService.purgePublishedBefore(LocalDateTime.now().plusDays(1));

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(outboxEventRepository.findAll()).extracting(OutboxEvent::getAggregateId).containsExactly("2");
        assertThat(outboxService.findPending(publishedId)).isEmpty();
    }
}
