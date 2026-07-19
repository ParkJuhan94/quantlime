package com.quantlab.infra.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** GET {chartBaseUrl}/marketindex/exchange/{pair}/prices 응답 원소 하나(일별 환율). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverExchangeRateCandleResponse(
    String localTradedAt,
    String closePrice
) {
}
