package com.quantlime.payment.dto.response;

import java.time.LocalDateTime;

public record PaymentResponse(
    String orderId,
    int amount,
    int installmentMonths,
    String status,
    boolean renewal,
    LocalDateTime approvedAt,
    LocalDateTime createdAt
) {
}
