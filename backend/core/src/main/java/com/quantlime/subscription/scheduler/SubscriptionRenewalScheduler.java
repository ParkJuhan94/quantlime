package com.quantlime.subscription.scheduler;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.common.util.SafeExecutor;
import com.quantlime.subscription.service.SubscriptionService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 구독 자동 갱신을 매일 21:00에 실행한다(운영 EC2가 00:00~07:40 중지돼 새벽 시각은 영영 못 돈다). 다른 배치(MarketDataRefreshService,
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

    @Scheduled(cron = "0 0 21 * * *", zone = "Asia/Seoul")
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
        // 실제 결제는 이 스레드가 아니라 Kafka 컨슈머(SubscriptionRenewalConsumer, event 모듈)가 건별로
        // 처리한다(2026-09-24). 2026-10-31부터는 이벤트를 바로 발행하지 않고 아웃박스에 적재한다 - 예전엔
        // "발행이 실패해도 다음날 배치가 다시 집어든다"고 봤지만, 대상 조회가 결제 예정일 = 오늘(등호)이라
        // 다음날엔 대상에서 빠져 결제 없이 ACTIVE가 계속 유지됐다(docs/00-sre/SRE.md §7-1). 지금은 브로커가
        // 멈춰 있어도 행이 남아 복구 뒤 릴레이가 보낸다. 한 건의 적재 실패가 나머지를 막지 않게 건별로 격리한다.
        dueSubscriptionIds.forEach(subscriptionId -> SafeExecutor.runSafely(
            "구독 갱신 요청 적재(subscriptionId=" + subscriptionId + ")",
            () -> subscriptionService.requestRenewal(subscriptionId)));
        SafeExecutor.runSafely("해지된 구독 만료 처리", subscriptionService::expireLapsedSubscriptions);
        log.info("구독 자동 갱신 이벤트 발행 종료: 대상={}건", dueSubscriptionIds.size());
    }
}
