package com.quantlime.price.dto.response;

import org.springframework.lang.Nullable;

public record TradeResponse(
    @Nullable Double price,
    @Nullable Double volume,
    String timestamp,
    String currency
) {
}
