package com.quantlime.price.dto.response;

public record CurrentPriceResponse(
    String stockCode,
    Long price,
    Double changeRate,
    String currency,
    String timestamp
) {
}
