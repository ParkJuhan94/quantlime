package com.quantlime.event.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.quantlime.infra.tosspayments.dto.TossPaymentApprovalResponse;
import com.quantlime.payment.implement.BillingProcessor;
import com.quantlime.subscription.SubscriptionFixture;
import com.quantlime.subscription.SubscriptionPlanFixture;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.repository.SubscriptionPlanRepository;
import com.quantlime.subscription.repository.SubscriptionRepository;
import com.quantlime.subscription.scheduler.SubscriptionRenewalScheduler;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * 작업 2(컨슈머 lag 관측) 완료 기준 확인용 실험 - CI에서는 돌지 않는다({@code KAFKA_LAG_EXPERIMENT=true}일 때만).
 * 결제 컨슈머의 핸들러를 일부러 멈춰 두고(외부 API 응답 대기 상황) 실제 리스너 컨테이너·실제 브로커에서
 * {@link KafkaConsumerLagMonitor}가 lag 발생과 stalled 증가를 보고하는지, 핸들러를 풀면 둘 다 0으로 돌아오는지 잰다.
 *
 * <pre>
 * KAFKA_LAG_EXPERIMENT=true ./gradlew :api:test --tests '*KafkaConsumerStallExperimentTest' --rerun
 * </pre>
 */
@Slf4j
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "KAFKA_LAG_EXPERIMENT", matches = "true")
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=true")
class KafkaConsumerStallExperimentTest extends ApiTestSupport {

    private static final String GROUP = "subscription-renewal-collector";
    private static final int SUBSCRIPTION_COUNT = 20;
    private static final int STUCK_SECONDS = 100;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionRenewalScheduler subscriptionRenewalScheduler;

    @Autowired
    private KafkaConsumerLagMonitor lagMonitor;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private BillingProcessor billingProcessor;

    @Test
    @DisplayName("[실험: 핸들러가 멈추면 lag와 stalled가 오르고, 풀면 0으로 돌아오는가]")
    void stuckHandlerShowsUpAsLagAndStalled() throws Exception {
        // given
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger charged = new AtomicInteger();
        given(billingProcessor.charge(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willAnswer(invocation -> {
                release.await();
                charged.incrementAndGet();
                String orderId = invocation.getArgument(2);
                return new TossPaymentApprovalResponse("pk-" + orderId, orderId, "QuantLime 구독", "DONE",
                    23700, "카드", Instant.now().toString());
            });
        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlanFixture.createPlan());
        for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
            User user = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "stall-" + i));
            Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
            subscription.scheduleRenewalRetry(LocalDate.now());
            subscriptionRepository.save(subscription);
        }
        subscriptionRenewalScheduler.renewDueSubscriptions();

        // when - 핸들러가 막힌 채 STUCK_SECONDS 동안 10초마다 수집
        double maxLag = 0;
        double maxStalled = 0;
        for (int elapsed = 10; elapsed <= STUCK_SECONDS; elapsed += 10) {
            Thread.sleep(10_000);
            lagMonitor.refresh();
            double lag = totalLag();
            double stalled = stalled();
            maxLag = Math.max(maxLag, lag);
            maxStalled = Math.max(maxStalled, stalled);
            log.info("[실험 샘플] 정지 {}초: 처리={}건 lag={} stalled={}초", elapsed, charged.get(), lag, stalled);
        }
        log.info("[실험] 핸들러 해제");
        release.countDown();
        Instant releasedAt = Instant.now();
        while (charged.get() < SUBSCRIPTION_COUNT && Duration.between(releasedAt, Instant.now()).getSeconds() < 120) {
            Thread.sleep(1000);
        }
        Thread.sleep(3000);
        lagMonitor.refresh();
        log.info("[실험 결과] 정지 중 최대 lag={}, 최대 stalled={}초, 해제 뒤 처리={}건 lag={} stalled={}초",
            maxLag, maxStalled, charged.get(), totalLag(), stalled());

        // then
        assertThat(maxLag).as("멈춘 동안 lag가 보여야 한다").isGreaterThan(0);
        assertThat(maxStalled).as("멈춘 시간이 stalled로 쌓여야 한다").isGreaterThanOrEqualTo(STUCK_SECONDS - 40);
        assertThat(totalLag()).as("풀고 나면 lag 해소").isZero();
        assertThat(stalled()).as("풀고 나면 stalled 해소").isZero();
    }

    private double totalLag() {
        return meterRegistry.find(KafkaConsumerLagMonitor.LAG).tag("group", GROUP).gauges().stream()
            .mapToDouble(Gauge::value).sum();
    }

    private double stalled() {
        Gauge gauge = meterRegistry.find(KafkaConsumerLagMonitor.STALLED).tag("group", GROUP).gauge();
        return gauge == null ? 0 : gauge.value();
    }
}
