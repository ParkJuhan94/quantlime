package com.quantlab.price.dto.response;

public record CurrentPriceResponse(
    String stockCode,
    Long price,
    String currency,
    String timestamp
) {
}
