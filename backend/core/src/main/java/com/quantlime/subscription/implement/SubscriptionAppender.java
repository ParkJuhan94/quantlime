package com.quantlime.subscription.implement;

import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 구독 저장을 감싸는 구현 레이어. 트랜잭션 경계는 호출하는 서비스가 소유한다. */
@Component
@RequiredArgsConstructor
public class SubscriptionAppender {

    private final SubscriptionRepository subscriptionRepository;

    public Subscription save(Subscription subscription) {
        return subscriptionRepository.save(subscription);
    }
}
