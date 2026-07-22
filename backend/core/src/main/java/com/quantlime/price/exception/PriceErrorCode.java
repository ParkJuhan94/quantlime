package com.quantlime.price.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PriceErrorCode implements ErrorCode {

    DAILY_PRICE_ALREADY_EXISTS("해당 날짜의 시세 데이터가 이미 존재합니다.", "PR_000"),
    FAILED_TO_COLLECT_PRICE("시세 데이터 수집에 실패했습니다.", "PR_001");

    private final String message;
    private final String code;
}
