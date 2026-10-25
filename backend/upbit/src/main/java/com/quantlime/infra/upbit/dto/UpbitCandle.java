package com.quantlime.infra.upbit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /v1/candles/{minutes/{unit}|days|weeks|months} 응답 원소 하나(OHLCV 전체). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpbitCandle(
    @JsonProperty("candle_date_time_utc") String candleDateTimeUtc,
    @JsonProperty("opening_price") double openingPrice,
    @JsonProperty("high_price") double highPrice,
    @JsonProperty("low_price") double lowPrice,
    @JsonProperty("trade_price") double tradePrice,
    @JsonProperty("candle_acc_trade_volume") double candleAccTradeVolume
) {
}
