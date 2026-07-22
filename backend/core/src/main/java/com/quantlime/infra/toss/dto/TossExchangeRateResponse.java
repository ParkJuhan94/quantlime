package com.quantlime.infra.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET /api/v1/exchange-rate 응답. 매매기준율(midRate)까지 스펙에 있지만
 * 위젯 표시에는 실제 매수 환율(rate)과 등락 방향(rateChangeType)만
 * 필요해 그 둘만 모델링한다. validFrom/validUntil은 보통 1분 단위로
 * 갱신되는 유효 구간이라 서버에서 짧게(MarketIndexCache) 캐싱한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossExchangeRateResponse(
    ExchangeRateResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExchangeRateResult(
        String baseCurrency,
        String quoteCurrency,
        String rate,
        String rateChangeType
    ) {
    }
}
