package com.quantlime.infra.tosspayments.dto;

public record TossBillingKeyResponse(
    String billingKey,
    String customerKey,
    String cardCompany,
    String cardNumber,
    String authenticatedAt
) {
}
