package com.quantlime.subscription.scheduler;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.common.util.SafeExecutor;
import com.quantlime.subscription.event.SubscriptionRenewalDueEvent;
import com.quantlime.subscription.service.SubscriptionService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 구독 자동 갱신을 매일 04:00에 실행한다. 다른 배치(MarketDataRefreshService,
 * FeedCollectionFacade 등)는 전부 RedisLockService.runExclusively로 분산락을
 * 쓰는데 이 스케줄러만 락이 없었다 - 인스턴스가 2대 이상이 되면 같은 구독이
 * 동시에 이중 결제될 수 있는 구조였다(docs/00-sre/SRE.md §5-1 참고, 2026-09-24
 * 추가). 지금은 인스턴스가 1대뿐이라 즉시 발동하는 버그는 아니지만, 수평
 * 확장을 막고 있던 2곳 중 하나라 다른 배치들과 같은 패턴으로 맞춰둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionRenewalScheduler {

    private static final String LOCK_KEY = "lock:subscription-renewal";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final RedisLockService redisLockService;
    private final SubscriptionService subscriptionService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void renewDueSubscriptions() {
        try {
            redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, () -> {
                runRenewalBatch();
                return true;
            }).ifPresentOrElse(
                result -> log.info("구독 자동 갱신 배치 완료"),
                () -> log.info("이미 다른 구독 자동 갱신이 진행 중 - 이번 실행은 스킵"));
        } catch (Exception e) {
            log.error("구독 자동 갱신 배치 실패: reason={}", e.getMessage(), e);
        }
    }

    private void runRenewalBatch() {
        List<Long> dueSubscriptionIds = subscriptionService.findSubscriptionIdsDueForRenewal();
        log.info("구독 자동 갱신 이벤트 발행: count={}", dueSubscriptionIds.size());
        // 실제 결제는 이제 이 스레드가 아니라 Kafka 컨슈머
        // (SubscriptionRenewalConsumer, event 모듈)가 건별로 처리한다
        // (2026-09-24, 카프카 다도메인 확장 Phase 2). 발행은 비동기 fire-and-forget이라
        // 이 루프는 브로커 실패로 중단되지 않는다 - 발행 실패는 KafkaEventSender가
        // ERROR 로그와 kafka.publish.failures 카운터로 남기고(2026-10-01), 그 구독은
        // 갱신 상태가 그대로라 다음날 04:00 배치가 다시 집어든다. 개별 결제
        // 실패/재시도는 PaymentService.chargeRenewal과 DLT 핸들러가 처리한다.
        dueSubscriptionIds.forEach(subscriptionId ->
            eventPublisher.publishEvent(new SubscriptionRenewalDueEvent(subscriptionId)));
        SafeExecutor.runSafely("해지된 구독 만료 처리", subscriptionService::expireLapsedSubscriptions);
        log.info("구독 자동 갱신 이벤트 발행 종료: 대상={}건", dueSubscriptionIds.size());
    }
}
