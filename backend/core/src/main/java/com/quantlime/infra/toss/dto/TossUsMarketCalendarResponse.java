package com.quantlime.infra.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET /api/v1/market-calendar/US 응답. 국내({@link TossMarketCalendarResponse})와
 * 형태가 다르다 - {@code integrated} 래퍼 없이 dayMarket(주간거래)/
 * preMarket/regularMarket/afterMarket 4개 세션이 평평하게 오고, today
 * 하나가 아니라 previousBusinessDay/today/nextBusinessDay 3영업일을
 * 준다. 모든 시각 KST(+09:00) - 미국 정규장(regularMarket)은 KST
 * 22:30~다음날 05:00으로 자정을 넘기므로, "지금이 장중인가"를 판정하려면
 * today의 세션만으로는 부족하고 3영업일 전부를 봐야 한다
 * (UsMarketCalendarCache 참고). 휴장일에는 국내와 동일하게 해당 날짜의
 * 세션 필드가 전부 null로 내려온다(실제 응답으로 미확인 - 국내 패턴과
 * 동일하다고 가정, null-safe하게 처리).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossUsMarketCalendarResponse(
    UsMarketCalendarResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsMarketCalendarResult(
        UsMarketDay previousBusinessDay,
        UsMarketDay today,
        UsMarketDay nextBusinessDay
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsMarketDay(
        String date,
        TossMarketCalendarResponse.MarketSession dayMarket,
        TossMarketCalendarResponse.MarketSession preMarket,
        TossMarketCalendarResponse.MarketSession regularMarket,
        TossMarketCalendarResponse.MarketSession afterMarket
    ) {
    }
}
