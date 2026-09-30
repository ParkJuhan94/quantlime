package com.quantlime.subscription;

import static lombok.AccessLevel.PRIVATE;

import com.quantlime.subscription.domain.SubscriptionPlan;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class SubscriptionPlanFixture {

    public static SubscriptionPlan createPlan() {
        return createPlan("PLAN_3M", "3개월 플랜", 3, 23700);
    }

    public static SubscriptionPlan createPlan(String code, String name, int months, int priceWon) {
        return SubscriptionPlan.of(code, name, months, priceWon);
    }
}
