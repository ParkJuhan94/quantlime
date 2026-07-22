package com.quantlime.backtest.domain;

import com.quantlime.backtest.exception.BacktestErrorCode;
import com.quantlime.common.exception.ValidationException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 백테스트 대상 서브스코어 축. 추세추종/평균회귀는 수익 실현 기간이 달라
 * (평균회귀=단기, 추세추종=중장기) 검증 단위를 분리한다(CLAUDE.md 백테스트
 * 계획 참고).
 */
@Getter
@RequiredArgsConstructor
public enum BacktestAxis {

    TREND("추세추종"),
    MEAN_REVERSION("평균회귀");

    private final String label;

    /**
     * 퀀트 엔진(Python)이 내려주는 축 코드("trend"/"mean_reversion")를
     * enum으로 변환한다. Grade/Quadrant의 of()와 달리 파이썬 쪽이 snake_case
     * 짧은 코드를 쓰므로, enum 이름과 완전히 같지 않다 - 별도 매핑 테이블로
     * 처리한다.
     */
    public static BacktestAxis of(String rawAxis) {
        if ("trend".equals(rawAxis)) {
            return TREND;
        }
        if ("mean_reversion".equals(rawAxis)) {
            return MEAN_REVERSION;
        }
        throw new ValidationException(BacktestErrorCode.INVALID_AXIS);
    }
}
