package com.quantlime.subscription.dto.mapper;

import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.dto.response.SubscriptionPlanResponse;
import com.quantlime.subscription.dto.response.SubscriptionResponse;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class SubscriptionMapper {

    public static SubscriptionPlanResponse toSubscriptionPlanResponse(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
            plan.getCode(), plan.getName(), plan.getBillingPeriodMonths(), plan.getPriceWon());
    }

    public static SubscriptionResponse toSubscriptionResponse(Subscription subscription) {
        SubscriptionPlan plan = subscription.getPlan();
        return new SubscriptionResponse(
            plan.getCode(),
            plan.getName(),
            subscription.getStatus().getLabel(),
            subscription.getCurrentPeriodStart(),
            subscription.getCurrentPeriodEnd(),
            subscription.getNextBillingAt(),
            subscription.isAutoRenew(),
            subscription.getInstallmentMonths()
        );
    }
}
