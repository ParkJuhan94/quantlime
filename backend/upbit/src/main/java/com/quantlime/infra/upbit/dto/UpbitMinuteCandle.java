package com.quantlime.infra.upbit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /v1/candles/minutes/{unit} 응답 원소 하나(분봉). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpbitMinuteCandle(
    @JsonProperty("candle_date_time_kst") String candleDateTimeKst,
    @JsonProperty("trade_price") double tradePrice
) {
}
