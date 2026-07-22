package com.quantlime.infra.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** GET /api/index/{code}/price 응답 배열의 원소 하나(일봉). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverIndexCandleResponse(
    String localTradedAt,
    String closePrice,
    String openPrice,
    String highPrice,
    String lowPrice
) {
}
