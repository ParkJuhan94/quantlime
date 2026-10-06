package com.quantlime.subscription.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.exception.SubscriptionErrorCode;
import com.quantlime.subscription.implement.SubscriptionPlanReader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    private final SubscriptionPlanReader subscriptionPlanReader;

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getActivePlans() {
        return subscriptionPlanReader.findAllByActiveTrueOrderByBillingPeriodMonthsAsc();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan getByCode(String code) {
        return subscriptionPlanReader.findByCode(code)
            .orElseThrow(() -> new NotFoundException(SubscriptionErrorCode.NOT_FOUND_SUBSCRIPTION_PLAN));
    }
}
