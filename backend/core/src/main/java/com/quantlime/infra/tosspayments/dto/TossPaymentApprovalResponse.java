package com.quantlime.infra.tosspayments.dto;

public record TossPaymentApprovalResponse(
    String paymentKey,
    String orderId,
    String orderName,
    String status,
    int totalAmount,
    String method,
    String approvedAt
) {
}
