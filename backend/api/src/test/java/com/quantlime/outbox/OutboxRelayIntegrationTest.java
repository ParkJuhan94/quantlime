package com.quantlime.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.quantlime.outbox.domain.OutboxEvent;
import com.quantlime.outbox.domain.OutboxStatus;
import com.quantlime.outbox.repository.OutboxEventRepository;
import com.quantlime.subscription.SubscriptionFixture;
import com.quantlime.subscription.SubscriptionPlanFixture;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.repository.SubscriptionPlanRepository;
import com.quantlime.subscription.repository.SubscriptionRepository;
import com.quantlime.subscription.service.SubscriptionService;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 구독 갱신 요청이 아웃박스에 적재된 뒤 커밋 직후 즉시 발행되고, 그 결과가 실제 DB에 PUBLISHED로 남는지 검증한다
 * (2026-10-31). 즉시 발행은 AFTER_COMMIT 리스너 안에서 상태를 기록하므로, 트랜잭션 전파를 잘못 두면 발행은 됐는데
 * 기록만 조용히 사라진다(그러면 폴링이 같은 행을 계속 다시 보낸다) - Mockito 단위 테스트로는 잡을 수 없어 실제
 * 트랜잭션·브로커로 확인한다.
 */
@Tag("integration")
class OutboxRelayIntegrationTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("[갱신 요청은 아웃박스에 한 번만 적재되고, 커밋 직후 즉시 발행돼 PUBLISHED로 기록된다]")
    void requestRenewal_publishedImmediatelyAndMarked() {
        // given
        User user = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "outbox-it"));
        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlanFixture.createPlan());
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        subscription.scheduleRenewalRetry(LocalDate.now());
        Long subscriptionId = subscriptionRepository.save(subscription).getId();

        // when - 같은 날 스케줄러가 두 번 돈 상황
        subscriptionService.requestRenewal(subscriptionId);
        subscriptionService.requestRenewal(subscriptionId);

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<OutboxEvent> rows = outboxEventRepository.findAll();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(rows.get(0).getAttemptCount()).isEqualTo(1);
            assertThat(rows.get(0).getDedupKey())
                .isEqualTo("SUBSCRIPTION_RENEWAL_DUE:" + subscriptionId + ":" + LocalDate.now());
        });
    }
}
