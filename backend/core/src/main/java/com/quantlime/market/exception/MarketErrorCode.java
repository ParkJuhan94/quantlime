package com.quantlime.market.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MarketErrorCode implements ErrorCode {

    BITCOIN_TICKER_NOT_FOUND("비트코인 시세 응답이 비어 있습니다.", "MK_000"),
    INVALID_AGGREGATION_INTERVAL("집계 단위는 weekly 또는 monthly여야 합니다.", "MK_001");

    private final String message;
    private final String code;
}
