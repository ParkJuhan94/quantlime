package com.quantlime.infra.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * GET /api/v1/market-indicators/prices 응답. 국내 지수(KOSPI/KOSDAQ)·국채
 * 등의 현재가 전용 - {@code lastPrice}만 내려오고 등락률·장중여부는 이
 * 응답에 없다(호출 측이 전일 종가 대비로 직접 계산해야 함,
 * MarketIndexCache 참고). {@code timestamp}는 데이터 미제공 시 null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossMarketIndicatorPriceResponse(
    List<MarketIndicatorPrice> result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketIndicatorPrice(
        String symbol,
        String timestamp,
        String lastPrice
    ) {
    }
}
