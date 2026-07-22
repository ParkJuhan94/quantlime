package com.quantlime.subscription.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.exception.SubscriptionErrorCode;
import com.quantlime.subscription.repository.SubscriptionPlanRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getActivePlans() {
        return subscriptionPlanRepository.findAllByActiveTrueOrderByBillingPeriodMonthsAsc();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan getByCode(String code) {
        return subscriptionPlanRepository.findByCode(code)
            .orElseThrow(() -> new NotFoundException(SubscriptionErrorCode.NOT_FOUND_SUBSCRIPTION_PLAN));
    }
}
