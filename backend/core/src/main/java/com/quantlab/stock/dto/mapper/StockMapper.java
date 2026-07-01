package com.quantlab.stock.dto.mapper;

import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.dto.response.StockDetailResponse;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class StockMapper {

    public static StockDetailResponse toStockDetailResponse(Stock stock) {
        return new StockDetailResponse(
            stock.getId(),
            stock.getStockCode(),
            stock.getStockName(),
            stock.getMarketType().getLabel(),
            stock.getListingStatus().getLabel(),
            stock.getSector()
        );
    }
}
