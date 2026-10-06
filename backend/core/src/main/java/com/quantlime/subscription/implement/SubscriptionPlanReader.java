package com.quantlime.subscription.implement;

import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.repository.SubscriptionPlanRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 구독 플랜 조회를 감싸는 구현 레이어(Implementation). 메서드 이름은 Repository와 같다. */
@Component
@RequiredArgsConstructor
public class SubscriptionPlanReader {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public Optional<SubscriptionPlan> findByCode(String code) {
        return subscriptionPlanRepository.findByCode(code);
    }

    public boolean existsByCode(String code) {
        return subscriptionPlanRepository.existsByCode(code);
    }

    public List<SubscriptionPlan> findAllByActiveTrueOrderByBillingPeriodMonthsAsc() {
        return subscriptionPlanRepository.findAllByActiveTrueOrderByBillingPeriodMonthsAsc();
    }
}
