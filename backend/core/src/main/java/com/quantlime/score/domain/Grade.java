package com.quantlime.score.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.common.util.EnumCodeMatcher;
import com.quantlime.score.exception.ScoreErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Grade {

    STRONG_BUY("강력매수"),
    BUY("매수"),
    NEUTRAL("중립"),
    SELL("매도"),
    STRONG_SELL("강력매도");

    private final String label;

    /**
     * 퀀트 엔진(Python)이 내려주는 등급 코드(STRONG_BUY/BUY/NEUTRAL/SELL/
     * STRONG_SELL 문자열)를 enum으로 변환한다. 다른 enum들의 of()와 달리
     * 한글 표시명이 아니라 등급 코드 자체로 매칭한다 - 소스 데이터(Python
     * 응답)가 코드 형태로 오기 때문.
     */
    public static Grade of(String rawGrade) {
        return EnumCodeMatcher.matchByName(Grade.class, rawGrade,
            () -> new ValidationException(ScoreErrorCode.INVALID_GRADE));
    }
}
