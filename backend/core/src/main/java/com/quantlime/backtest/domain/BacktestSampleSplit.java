package com.quantlime.backtest.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 횡단면 백테스트 결과가 어느 표본에서 나왔는지 구분한다. 지금은
 * {@code FULL}(선정된 유니버스 전체)만 실제로 쓰인다 - TUNE/VALIDATE는
 * Phase G 튜닝에서 종목을 두 그룹으로 나눠 한쪽(TUNE)에서 고른 파라미터를
 * 다른 쪽(VALIDATE)으로 검증하는 표본분할 규율을 위해 스키마에 미리
 * 자리를 만들어 둔 것 - 실제 분할 로직은 아직 구현하지 않았다
 * (quant-engine/docs/BACKTEST_METHODOLOGY_REVIEW.md 참고).
 */
@Getter
@RequiredArgsConstructor
public enum BacktestSampleSplit {

    FULL("전체"),
    TUNE("튜닝용"),
    VALIDATE("검증용");

    private final String label;
}
