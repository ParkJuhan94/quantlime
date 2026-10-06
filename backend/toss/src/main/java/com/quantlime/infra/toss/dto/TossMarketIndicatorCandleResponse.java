package com.quantlime.infra.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * GET /api/v1/market-indicators/{symbol}/candles 응답. {@code interval}이
 * {@code 1m}(분봉)인 건 KOSPI/KOSDAQ만 지원하고, 국채(KR_BOND_*)는
 * {@code 1d}(일봉)만 지원한다(호출 측이 지수 코드에 맞게 interval을
 * 선택해야 함). {@code volume}이 국내 캔들(TossCandleResponse)과 달리
 * 포함돼 있지만, 지수 자체는 currency 개념이 없어 그 필드는 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossMarketIndicatorCandleResponse(
    MarketIndicatorCandlePageResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketIndicatorCandlePageResult(
        List<MarketIndicatorCandle> candles,
        String nextBefore
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketIndicatorCandle(
        String timestamp,
        String openPrice,
        String highPrice,
        String lowPrice,
        String closePrice,
        String volume
    ) {
    }
}
