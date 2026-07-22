package com.quantlime.market.dto.response;

public record MarketRankingResponse(
    String stockCode,
    String stockName,
    String sector,
    Long currentPrice,
    Double changeRate
) {
}
