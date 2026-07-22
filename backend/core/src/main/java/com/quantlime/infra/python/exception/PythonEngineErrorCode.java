package com.quantlime.infra.python.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PythonEngineErrorCode implements ErrorCode {

    SCORE_CALCULATION_FAILED("퀀트 엔진 스코어 계산에 실패했습니다.", "PYE_000");

    private final String message;
    private final String code;
}
