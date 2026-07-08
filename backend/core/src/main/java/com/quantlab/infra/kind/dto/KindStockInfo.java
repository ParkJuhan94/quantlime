package com.quantlab.infra.kind.dto;

import com.quantlab.stock.domain.MarketType;

public record KindStockInfo(
    String stockCode,
    String stockName,
    MarketType marketType,
    String sector) {
}
