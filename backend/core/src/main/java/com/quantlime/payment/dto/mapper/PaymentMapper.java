package com.quantlime.payment.dto.mapper;

import com.quantlime.payment.domain.Payment;
import com.quantlime.payment.dto.response.PaymentResponse;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class PaymentMapper {

    public static PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
            payment.getOrderId(),
            payment.getAmount(),
            payment.getInstallmentMonths(),
            payment.getStatus().getLabel(),
            payment.isRenewal(),
            payment.getApprovedAt(),
            payment.getCreatedAt()
        );
    }
}
