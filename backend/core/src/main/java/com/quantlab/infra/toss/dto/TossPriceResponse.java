package com.quantlab.infra.toss.dto;

import java.util.List;

public record TossPriceResponse(
    List<TossPrice> result
) {

    public record TossPrice(
        String symbol,
        String timestamp,
        String lastPrice,
        String currency
    ) {
    }
}
