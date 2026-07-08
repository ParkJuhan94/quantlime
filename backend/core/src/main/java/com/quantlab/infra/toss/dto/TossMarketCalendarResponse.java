package com.quantlab.infra.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET /api/v1/market-calendar/KR 응답. "통합 모드(KRX+NXT)" 기준으로
 * preMarket(NXT 프리마켓, 08:00~09:00)/regularMarket(정규장,
 * 09:00~15:30)/afterMarket(NXT 애프터마켓, 15:30~20:00) 3개 세션을 준다.
 * 실제 스펙엔 previousBusinessDay/nextBusinessDay,
 * singlePriceAuctionStartTime 등 더 많은 필드가 있지만, 장중 판별
 * (MarketCalendarCache)에 필요한 today.integrated의 3개 세션만
 * 모델링하고 나머지는 무시한다. 휴장일에는 today.integrated 자체가
 * null로 내려온다.
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
        MarketSession preMarket,
        MarketSession regularMarket,
        MarketSession afterMarket
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketSession(
        String startTime,
        String endTime
    ) {
    }
}
