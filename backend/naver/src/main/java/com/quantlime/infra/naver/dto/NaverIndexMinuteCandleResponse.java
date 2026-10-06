package com.quantlime.infra.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** GET {chartBaseUrl}/chart/domestic/index/{code}/minute 응답 배열의 원소 하나(분봉). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverIndexMinuteCandleResponse(
    String localDateTime,
    double currentPrice
) {
}
