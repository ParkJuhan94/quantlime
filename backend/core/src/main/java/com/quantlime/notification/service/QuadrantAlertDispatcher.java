package com.quantlime.notification.service;

import com.quantlime.market.event.ScoreBatchCompletedEvent;
import com.quantlime.notification.event.QuadrantAlertRequestedEvent;
import com.quantlime.subscription.domain.SubscriptionStatus;
import com.quantlime.subscription.implement.SubscriptionReader;
import com.quantlime.watchlist.implement.WatchlistGroupReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 스코어 배치가 끝나면 사분면 변화 알림을 켠 구독자마다 판정 요청 이벤트를 하나씩 발행한다
 * (실제 판정·발송은 Kafka 컨슈머가 사용자 단위로 처리). 스코어가 구독자 전용 기능이라
 * 구독이 끊긴 사용자는 옵트인 상태가 남아 있어도 여기서 걸러진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuadrantAlertDispatcher {

    private final WatchlistGroupReader watchlistGroupReader;
    private final SubscriptionReader subscriptionReader;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void onScoreBatchCompleted(ScoreBatchCompletedEvent event) {
        List<Long> optedInUserIds = watchlistGroupReader.findUserIdsWithQuadrantAlertEnabled();
        if (optedInUserIds.isEmpty()) {
            return;
        }
        Set<Long> subscriberIds = new HashSet<>(subscriptionReader.findAllUserIdsByStatus(SubscriptionStatus.ACTIVE));
        List<Long> targets = optedInUserIds.stream().filter(subscriberIds::contains).toList();
        targets.forEach(userId -> eventPublisher.publishEvent(new QuadrantAlertRequestedEvent(userId)));
        log.info("사분면 변화 알림 판정 요청 발행: 대상 {}명(옵트인 {}명 중 구독자)", targets.size(), optedInUserIds.size());
    }
}
