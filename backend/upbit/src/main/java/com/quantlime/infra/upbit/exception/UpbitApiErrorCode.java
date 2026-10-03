package com.quantlime.infra.upbit.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UpbitApiErrorCode implements ErrorCode {

    TICKER_INQUIRY_FAILED("Upbit API 시세 조회에 실패했습니다.", "UPBIT_000"),
    CANDLE_INQUIRY_FAILED("Upbit API 캔들 조회에 실패했습니다.", "UPBIT_001");

    private final String message;
    private final String code;
}
