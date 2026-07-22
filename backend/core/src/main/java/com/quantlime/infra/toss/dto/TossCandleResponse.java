package com.quantlime.infra.toss.dto;

import java.util.List;

public record TossCandleResponse(
    TossCandlePageResult result
) {

    public record TossCandlePageResult(
        List<TossCandle> candles,
        String nextBefore
    ) {
    }

    public record TossCandle(
        String timestamp,
        String openPrice,
        String highPrice,
        String lowPrice,
        String closePrice,
        String volume,
        String currency
    ) {
    }
}
