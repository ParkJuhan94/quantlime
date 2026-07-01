package com.quantlab.infra.toss.exception;

import com.quantlab.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TossApiErrorCode implements ErrorCode {

    TOKEN_ISSUANCE_FAILED("토스증권 API 토큰 발급에 실패했습니다.", "TOSS_000"),
    PRICE_INQUIRY_FAILED("토스증권 API 시세 조회에 실패했습니다.", "TOSS_001"),
    CANDLE_INQUIRY_FAILED("토스증권 API 캔들 조회에 실패했습니다.", "TOSS_002"),
    STOCK_INFO_INQUIRY_FAILED("토스증권 API 종목 정보 조회에 실패했습니다.", "TOSS_003"),
    RATE_LIMIT_EXCEEDED("토스증권 API 요청 한도를 초과했습니다.", "TOSS_004"),
    INVALID_RESPONSE("토스증권 API 응답이 유효하지 않습니다.", "TOSS_005");

    private final String message;
    private final String code;
}
