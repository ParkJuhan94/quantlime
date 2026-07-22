package com.quantlime.stock.dto.response;

public record StockDetailResponse(
    Long id,
    String stockCode,
    String stockName,
    String marketType,
    String listingStatus,
    String sector,
    String logoUrl
) {
}
