package com.quantlime.stock.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StockErrorCode implements ErrorCode {

    NOT_FOUND_STOCK("해당 종목을 찾을 수 없습니다.", "ST_000"),
    INVALID_MARKET_TYPE("올바른 시장 유형을 입력해주세요.", "ST_001"),
    INVALID_LISTING_STATUS("올바른 상장 상태를 입력해주세요.", "ST_002");

    private final String message;
    private final String code;
}
