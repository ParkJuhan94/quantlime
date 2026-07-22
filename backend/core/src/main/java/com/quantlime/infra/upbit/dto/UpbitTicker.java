package com.quantlime.infra.upbit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /v1/ticker 응답 원소 하나. Upbit는 인증이 필요 없는 공개 REST API지만
 * JSON 필드가 snake_case라 필요한 두 필드만 {@link JsonProperty}로 매핑하고
 * 나머지(52주 최고가 등)는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpbitTicker(
    String market,
    @JsonProperty("trade_price") Long tradePrice,
    @JsonProperty("signed_change_rate") Double signedChangeRate
) {
}
