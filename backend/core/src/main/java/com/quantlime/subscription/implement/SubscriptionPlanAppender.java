package com.quantlime.subscription.implement;

import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 구독 플랜 저장·삭제를 감싸는 구현 레이어. 트랜잭션 경계는 호출하는 서비스가 소유한다(파생 delete는 Repository의 @Transactional도 그대로 유효). */
@Component
@RequiredArgsConstructor
public class SubscriptionPlanAppender {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public SubscriptionPlan save(SubscriptionPlan plan) {
        return subscriptionPlanRepository.save(plan);
    }
}
