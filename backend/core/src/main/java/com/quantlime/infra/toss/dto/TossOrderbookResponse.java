package com.quantlime.infra.toss.dto;

import java.util.List;

public record TossOrderbookResponse(
    Orderbook result
) {

    public record Orderbook(
        String timestamp,
        String currency,
        List<OrderbookEntry> asks,
        List<OrderbookEntry> bids
    ) {
    }

    public record OrderbookEntry(
        String price,
        String volume
    ) {
    }
}
