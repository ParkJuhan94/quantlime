package com.quantlime.infra.python.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PythonEngineErrorCode implements ErrorCode {

    SCORE_CALCULATION_FAILED("퀀트 엔진 스코어 계산에 실패했습니다.", "PYE_000"),
    BACKTEST_CALCULATION_FAILED("퀀트 엔진 백테스트 계산에 실패했습니다.", "PYE_001"),
    TRANSCRIPT_FETCH_FAILED("퀀트 엔진 자막 조회에 실패했습니다.", "PYE_002"),
    SUMMARY_GENERATION_FAILED("퀀트 엔진 AI 요약 생성에 실패했습니다.", "PYE_003"),
    SUMMARY_RATE_LIMIT_EXCEEDED("퀀트 엔진 AI 요약 생성이 Gemini 무료 티어 요청 한도에 걸렸습니다.", "PYE_004");

    private final String message;
    private final String code;
}
