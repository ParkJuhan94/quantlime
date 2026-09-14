package com.quantlime.score.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.common.util.EnumCodeMatcher;
import com.quantlime.score.exception.ScoreErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 횡단면 백분위를 산출할 때 비교 대상이 된 모집단 - 같은 날짜라도 국내/해외
 * 종목을 한 모집단으로 섞으면 시장별 분포 차이 때문에 백분위 의미가
 * 희석된다(2026-09 감사 세션, {@code /api/dashboard/scores?scope=} 필터와
 * 동일한 국내/해외 2분할). 나중에 시장별(KOSPI/KOSDAQ 등)로 더 세분해도
 * 과거 값의 "어느 모집단 기준이었는지"를 그대로 해석할 수 있도록 값 자체를
 * 저장해 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum PeerGroup {

    DOMESTIC("domestic"),
    OVERSEAS("overseas");

    private final String wireValue;

    /** quant-engine이 요청/응답에 쓰는 소문자 문자열(peer_group)과 매칭한다. */
    public static PeerGroup of(String rawValue) {
        return EnumCodeMatcher.matchByName(PeerGroup.class, rawValue,
            () -> new ValidationException(ScoreErrorCode.INVALID_PEER_GROUP));
    }
}
