package com.quantlime.price.dto.response;

public record TradeResponse(
    Double price,
    Double volume,
    String timestamp,
    String currency
) {
}
