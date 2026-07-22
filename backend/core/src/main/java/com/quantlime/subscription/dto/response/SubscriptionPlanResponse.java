package com.quantlime.subscription.dto.response;

public record SubscriptionPlanResponse(
    String code,
    String name,
    int billingPeriodMonths,
    int priceWon
) {
}
