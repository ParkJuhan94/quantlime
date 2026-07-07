package com.quantlab.score.domain;

import com.quantlab.common.exception.ValidationException;
import com.quantlab.common.util.EnumCodeMatcher;
import com.quantlab.score.exception.ScoreErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Grade {

    SSS("최우수"),
    SS("매우 우수"),
    S("우수"),
    A("양호"),
    B("보통"),
    C("주의"),
    D("위험");

    private final String label;

    /**
     * 퀀트 엔진(Python)이 내려주는 등급 코드(SSS/SS/S/A/B/C/D 문자열)를 enum으로
     * 변환한다. 다른 enum들의 of()와 달리 한글 표시명이 아니라 등급 코드
     * 자체로 매칭한다 - 소스 데이터(Python 응답)가 코드 형태로 오기 때문.
     */
    public static Grade of(String rawGrade) {
        return EnumCodeMatcher.matchByName(Grade.class, rawGrade,
            () -> new ValidationException(ScoreErrorCode.INVALID_GRADE));
    }
}
