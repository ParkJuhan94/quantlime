package com.quantlime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;

/**
 * 작업 1(발행 유실 점검) 측정용 실험 - CI에서는 돌지 않는다({@code KAFKA_OUTAGE_EXPERIMENT=true}일 때만).
 * 브로커가 멈춘 채 구독 자동 갱신 배치가 돌면 몇 건이 결제 컨슈머까지 도달하는지 센다.
 *
 * <p>실험 절차: 오늘이 결제일인 구독 N건 → Redpanda {@code docker pause} → 갱신 배치 실행 → OUTAGE_SECONDS 대기 →
 * unpause → 컨슈머 도달 대기. 장애 시간이 프로듀서 {@code delivery.timeout.ms}(기본 120초)보다 짧으면 프로듀서
 * 버퍼에 남아 있던 레코드가 재개 뒤 그대로 전송될 수 있어, 그보다 긴 경우와 짧은 경우를 따로 잰다.
 *
 * <pre>
 * KAFKA_OUTAGE_EXPERIMENT=true OUTAGE_SECONDS=150 ./gradlew :api:test --tests '*KafkaOutageRecoveryExperimentTest'
 * </pre>
 */
@Slf4j
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "KAFKA_OUTAGE_EXPERIMENT", matches = "true")
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=true")
class KafkaOutageRecoveryExperimentTest extends ApiTestSupport {

    private static final int SUBSCRIPTION_COUNT = 20;
    private static final Duration CONSUME_WAIT = Duration.ofSeconds(180);
    // 모두 도착한 뒤에도 늦게 오는 중복 전달이 있는지 더 지켜보는 시간
    private static final Duration DUPLICATE_WATCH = Duration.ofSeconds(30);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionRenewalScheduler subscriptionRenewalScheduler;

    @MockBean
    private BillingProcessor billingProcessor;

    @Test
    @DisplayName("[실험: 브로커 정지 중 구독 갱신 배치가 돌면 재개 뒤 몇 건이 결제 컨슈머에 도달하는가]")
    void renewalBatchDuringBrokerOutage() throws Exception {
        // given
        long outageSeconds = Long.parseLong(System.getenv().getOrDefault("OUTAGE_SECONDS", "150"));
        AtomicInteger chargeCalls = new AtomicInteger();
        Set<String> chargedOrderIds = ConcurrentHashMap.newKeySet();
        given(billingProcessor.charge(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willAnswer(invocation -> {
                String orderId = invocation.getArgument(2);
                chargeCalls.incrementAndGet();
                chargedOrderIds.add(orderId);
                return new TossPaymentApprovalResponse("pk-" + orderId, orderId, "QuantLime 구독", "DONE",
                    23700, "카드", Instant.now().toString());
            });
        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlanFixture.createPlan());
        List<Long> subscriptionIds = new ArrayList<>();
        for (int i = 0; i < SUBSCRIPTION_COUNT; i++) {
            User user = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "outage-" + i));
            Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
            subscription.scheduleRenewalRetry(LocalDate.now());
            subscriptionIds.add(subscriptionRepository.save(subscription).getId());
        }

        // when
        DockerClient docker = DockerClientFactory.instance().client();
        String containerId = findRedpandaContainerId(docker);
        docker.pauseContainerCmd(containerId).exec();
        long batchMillis;
        try {
            long started = System.nanoTime();
            subscriptionRenewalScheduler.renewDueSubscriptions();
            batchMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            log.info("[실험] 브로커 정지 중 갱신 배치 소요: {}ms, 정지 유지 {}초", batchMillis, outageSeconds);
            Thread.sleep(Duration.ofSeconds(outageSeconds).toMillis());
        } finally {
            docker.unpauseContainerCmd(containerId).exec();
        }
        Instant resumedAt = Instant.now();
        Instant deadline = resumedAt.plus(CONSUME_WAIT);
        while (chargedOrderIds.size() < SUBSCRIPTION_COUNT && Instant.now().isBefore(deadline)) {
            Thread.sleep(1000);
        }
        long recoverySeconds = Duration.between(resumedAt, Instant.now()).toSeconds();
        Thread.sleep(DUPLICATE_WATCH.toMillis());

        // then
        int delivered = chargedOrderIds.size();
        log.info("[실험 결과] 장애 {}초, 대상 {}건, 컨슈머 도달 {}건, 유실 {}건, 결제 호출 {}회(중복 {}회), "
                + "재개 후 도달까지 {}초, 배치 소요 {}ms, 구독={}",
            outageSeconds, SUBSCRIPTION_COUNT, delivered, SUBSCRIPTION_COUNT - delivered, chargeCalls.get(),
            chargeCalls.get() - delivered, recoverySeconds, batchMillis, subscriptionIds);
        assertThat(delivered).as("컨슈머까지 도달한 갱신 건수").isEqualTo(SUBSCRIPTION_COUNT);
        assertThat(chargeCalls.get()).as("결제 호출 횟수(중복 없음)").isEqualTo(SUBSCRIPTION_COUNT);
        assertThat(subscriptionRepository.findAllById(subscriptionIds))
            .allSatisfy(subscription -> assertThat(subscription.getRenewalFailureCount()).isZero());
    }

    // api 테스트 클래스패스엔 RedpandaContainer 타입이 없어(core testFixtures 내부 의존) 이 JVM 세션이 띄운
    // 컨테이너를 Testcontainers 세션 라벨과 이미지 이름으로 찾는다.
    private static String findRedpandaContainerId(DockerClient docker) {
        return docker.listContainersCmd()
            .withLabelFilter(Map.of("org.testcontainers.sessionId", DockerClientFactory.SESSION_ID))
            .exec()
            .stream()
            .filter(container -> container.getImage().contains("redpanda"))
            .map(Container::getId)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("이 테스트 세션의 Redpanda 컨테이너를 찾지 못했습니다"));
    }
}
