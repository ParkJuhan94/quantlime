package com.quantlime.infra.toss.dto;

import java.util.List;

public record TossStockInfoResponse(
    List<TossStockInfo> result
) {

    public record TossStockInfo(
        String symbol,
        String market
    ) {
    }
}
