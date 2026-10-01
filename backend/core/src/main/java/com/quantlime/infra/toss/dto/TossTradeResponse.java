package com.quantlime.infra.toss.dto;

import java.util.List;

public record TossTradeResponse(
    List<TossTrade> result
) {

    public record TossTrade(
        String price,
        String volume,
        String timestamp,
        String currency
    ) {
    }
}
