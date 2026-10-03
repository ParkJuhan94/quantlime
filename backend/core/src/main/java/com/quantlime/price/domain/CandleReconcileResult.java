package com.quantlime.price.domain;

/**
 * 이미 저장된 일봉 행에 새로 받은 캔들을 반영한 결과 - 수집 흐름(Collector)은 이 값만 보고
 * 저장 여부와 수정주가 소급 재조정 후속 처리를 결정한다.
 */
public enum CandleReconcileResult {
    /** 반영할 변경이 없다(저장 불필요). */
    UNCHANGED,
    /** 값이 갱신됐다. */
    UPDATED,
    /** 값이 갱신됐고, 종가 변동이 재조정(액면분할/병합) 임계값 이상이라 전 구간 재백필 후보다. */
    RESTATED
}
