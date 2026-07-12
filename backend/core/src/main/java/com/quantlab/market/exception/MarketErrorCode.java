package com.quantlab.market.exception;

import com.quantlab.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MarketErrorCode implements ErrorCode {

    BITCOIN_TICKER_NOT_FOUND("비트코인 시세 응답이 비어 있습니다.", "MK_000");

    private final String message;
    private final String code;
}
