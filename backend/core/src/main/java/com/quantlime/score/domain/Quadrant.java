package com.quantlime.score.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.common.util.EnumCodeMatcher;
import com.quantlime.score.exception.ScoreErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 추세추종/평균회귀 두 축을 4사분면으로 분류한 라벨. 종합점수(두 축 평균)만
 * 보면 상반된 두 상태(예: 상승추세 눌림목 vs 추세 연장·과열)가 비슷한
 * 값으로 뭉개지는 문제(CLAUDE.md 백테스트 계획 Phase D 참고)를 보완한다.
 */
@Getter
@RequiredArgsConstructor
public enum Quadrant {

    TREND_UP_OVERSOLD("상승추세 눌림목"),
    TREND_UP_OVERBOUGHT("추세 연장·과열"),
    TREND_DOWN_OVERSOLD("낙폭과대·위험"),
    TREND_DOWN_OVERBOUGHT("하락추세 반등실패");

    private final String label;

    /**
     * 퀀트 엔진(Python)이 내려주는 사분면 코드(trend_up_oversold 등,
     * quant-engine/calculator/scorer.py의 _classify_quadrant 참고)를
     * enum으로 변환한다. Grade.of()와 동일하게 한글 표시명이 아니라
     * 코드 자체로 매칭한다(소스 데이터가 코드 형태로 오기 때문).
     */
    public static Quadrant of(String rawQuadrant) {
        return EnumCodeMatcher.matchByName(Quadrant.class, rawQuadrant,
            () -> new ValidationException(ScoreErrorCode.INVALID_QUADRANT));
    }
}
