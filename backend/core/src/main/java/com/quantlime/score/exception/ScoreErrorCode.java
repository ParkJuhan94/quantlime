package com.quantlime.score.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScoreErrorCode implements ErrorCode {

    NOT_FOUND_SCORE("아직 계산된 스코어가 없습니다.", "SC_000"),
    INVALID_GRADE("유효하지 않은 등급입니다.", "SC_001"),
    INVALID_QUADRANT("유효하지 않은 사분면 코드입니다.", "SC_002");

    private final String message;
    private final String code;
}
