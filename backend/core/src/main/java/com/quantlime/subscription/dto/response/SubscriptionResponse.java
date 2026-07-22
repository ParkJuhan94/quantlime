package com.quantlime.subscription.dto.response;

import java.time.LocalDate;

public record SubscriptionResponse(
    String planCode,
    String planName,
    String status,
    LocalDate currentPeriodStart,
    LocalDate currentPeriodEnd,
    LocalDate nextBillingAt,
    boolean autoRenew,
    int installmentMonths
) {
}
