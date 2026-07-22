package com.quantlime.subscription;

import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.user.domain.User;
import java.time.LocalDate;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class SubscriptionFixture {

    public static Subscription createSubscription(User user, SubscriptionPlan plan) {
        return Subscription.activate(user, plan, "test-billing-key", 0, LocalDate.now());
    }
}
