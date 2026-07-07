package com.quantlab.infra.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET /api/v1/market-calendar/KR 응답. 실제 스펙엔 previousBusinessDay/
 * nextBusinessDay, preMarket/afterMarket, singlePriceAuctionStartTime 등
 * 더 많은 필드가 있지만, 장중 판별(MarketCalendarCache)에 필요한
 * today.integrated.regularMarket만 모델링하고 나머지는 무시한다.
 * 휴장일에는 today.integrated 자체가 null로 내려온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossMarketCalendarResponse(
    KrMarketCalendarResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KrMarketCalendarResult(
        MarketDay today
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketDay(
        String date,
        MarketSessions integrated
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketSessions(
        MarketSession regularMarket
    ) {
    }

    public record MarketSession(
        String startTime,
        String endTime
    ) {
    }
}
