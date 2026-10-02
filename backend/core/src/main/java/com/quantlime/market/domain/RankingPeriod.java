package com.quantlime.market.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.market.exception.MarketErrorCode;
import java.util.Arrays;
import lombok.Getter;

/**
 * 실시간 랭킹 기간 드롭다운 값. 토스 `/api/v1/rankings`의 duration 값과 1:1로
 * 대응하고({@link #tossDuration}), 스코어 정렬은 {@link #days} 만큼 이전
 * 스코어 대비 변화량으로 계산한다.
 *
 * <p>REALTIME의 토스 duration은 "1d"다 - 거래량/거래대금 정렬에서 realtime이
 * 거래량을 심각하게 과소집계해 전 sort를 1d로 통일했기 때문
 * ({@code TossMarketRankingCache} 주석 참고, 2026-09-29).
 */
@Getter
public enum RankingPeriod {

    REALTIME("realtime", "1d", 0),
    DAY("1d", "1d", 1),
    WEEK("1w", "1w", 7),
    MONTH("1mo", "1mo", 30),
    QUARTER("3mo", "3mo", 90),
    HALF_YEAR("6mo", "6mo", 180),
    YEAR("1y", "1y", 365);

    private final String code;
    private final String tossDuration;
    private final int days;

    RankingPeriod(String code, String tossDuration, int days) {
        this.code = code;
        this.tossDuration = tossDuration;
        this.days = days;
    }

    /** 실시간/1일은 이미 "오늘" 기준 값이라 별도 기간 계산이 필요 없다. */
    public boolean isIntraday() {
        return this == REALTIME || this == DAY;
    }

    public static RankingPeriod of(String code) {
        return Arrays.stream(values())
            .filter(value -> value.code.equals(code))
            .findFirst()
            .orElseThrow(() -> new ValidationException(MarketErrorCode.INVALID_RANKING_PERIOD));
    }
}
