package com.quantlime.backtest.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BacktestErrorCode implements ErrorCode {

    NOT_FOUND_BACKTEST_RESULT("아직 계산된 백테스트 결과가 없습니다.", "BT_000"),
    INVALID_AXIS("유효하지 않은 백테스트 축 코드입니다.", "BT_001"),
    UNSUPPORTED_MARKET("이 시장은 아직 백테스트 벤치마크가 없어 지원하지 않습니다.", "BT_002"),
    INSUFFICIENT_HISTORY("백테스트에 필요한 OHLCV/벤치마크 이력이 부족합니다.", "BT_003");

    private final String message;
    private final String code;
}
