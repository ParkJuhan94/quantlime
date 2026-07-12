package com.quantlab.infra.upbit.exception;

import com.quantlab.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UpbitApiErrorCode implements ErrorCode {

    TICKER_INQUIRY_FAILED("Upbit API 시세 조회에 실패했습니다.", "UPBIT_000");

    private final String message;
    private final String code;
}
