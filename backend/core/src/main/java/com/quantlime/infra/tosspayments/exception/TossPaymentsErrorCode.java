package com.quantlime.infra.tosspayments.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TossPaymentsErrorCode implements ErrorCode {

    BILLING_KEY_ISSUANCE_FAILED("토스페이먼츠 빌링키 발급에 실패했습니다.", "TOSSPAY_000"),
    PAYMENT_CHARGE_FAILED("토스페이먼츠 결제 승인에 실패했습니다.", "TOSSPAY_001"),
    PAYMENT_CANCEL_FAILED("토스페이먼츠 결제 취소에 실패했습니다.", "TOSSPAY_002"),
    RATE_LIMIT_EXCEEDED("토스페이먼츠 API 요청 한도를 초과했습니다.", "TOSSPAY_003");

    private final String message;
    private final String code;
}
