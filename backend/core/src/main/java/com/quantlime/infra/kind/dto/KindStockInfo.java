package com.quantlime.infra.kind.dto;

import com.quantlime.stock.domain.MarketType;

public record KindStockInfo(
    String stockCode,
    String stockName,
    MarketType marketType,
    String sector) {
}
