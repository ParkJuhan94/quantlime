package com.quantlime.subscription.scheduler;

import com.quantlime.common.util.SafeExecutor;
import com.quantlime.payment.service.PaymentService;
import com.quantlime.subscription.service.SubscriptionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionRenewalScheduler {

    private final SubscriptionService subscriptionService;
    private final PaymentService paymentService;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void renewDueSubscriptions() {
        List<Long> dueSubscriptionIds = subscriptionService.findSubscriptionIdsDueForRenewal();
        log.info("구독 자동 갱신 대상: count={}", dueSubscriptionIds.size());
        for (Long subscriptionId : dueSubscriptionIds) {
            // chargeRenewal 내부에서 이미 실패를 흡수하지만(재시도/PAST_DUE
            // 전환), 그 외 예상 못한 예외(레코드 삭제 등)까지 한 건의
            // 실패가 배치 전체를 막지 않도록 한 번 더 감싼다.
            SafeExecutor.runSafely(
                "구독 자동 갱신(subscriptionId=" + subscriptionId + ")",
                () -> paymentService.chargeRenewal(subscriptionId));
        }
        SafeExecutor.runSafely("해지된 구독 만료 처리", subscriptionService::expireLapsedSubscriptions);
    }
}
