package com.quantlime.market.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.market.exception.MarketErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 투자자별 매매대금 집계 단위. 사용자 요청 범위가 주간/월간뿐이라(일별은
 * Toss가 지원해도 이 프로젝트에서는 저장하지 않는다) 이 둘만 정의한다.
 */
@Getter
@RequiredArgsConstructor
public enum AggregationInterval {

    WEEKLY("주간", "1w", "weekly"),
    MONTHLY("월간", "1mo", "monthly");

    private final String label;
    // Toss investor-trading API의 interval 쿼리 파라미터 값.
    private final String tossValue;
    // 이 프로젝트 GET /api/market/indices/{code}/investor-trading의 interval 쿼리 파라미터 값.
    private final String queryValue;

    public static AggregationInterval fromQueryValue(String queryValue) {
        for (AggregationInterval interval : values()) {
            if (interval.queryValue.equals(queryValue)) {
                return interval;
            }
        }
        throw new ValidationException(MarketErrorCode.INVALID_AGGREGATION_INTERVAL);
    }
}
