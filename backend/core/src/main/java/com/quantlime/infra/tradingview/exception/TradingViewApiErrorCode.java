package com.quantlime.infra.tradingview.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TradingViewApiErrorCode implements ErrorCode {

    SYMBOL_QUOTE_INQUIRY_FAILED("TradingView API 시세 조회에 실패했습니다.", "TRADINGVIEW_000");

    private final String message;
    private final String code;
}
