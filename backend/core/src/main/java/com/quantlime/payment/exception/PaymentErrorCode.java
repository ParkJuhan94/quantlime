package com.quantlime.payment.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    BILLING_KEY_ISSUANCE_FAILED("빌링키 발급에 실패했습니다.", "PAY_000"),
    PAYMENT_CHARGE_FAILED("결제 승인에 실패했습니다.", "PAY_001"),
    NOT_FOUND_PAYMENT("결제 내역을 찾을 수 없습니다.", "PAY_002"),
    INVALID_WEBHOOK_SIGNATURE("웹훅 서명이 유효하지 않습니다.", "PAY_003");

    private final String message;
    private final String code;
}
