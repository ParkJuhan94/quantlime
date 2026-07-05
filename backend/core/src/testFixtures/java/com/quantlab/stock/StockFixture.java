package com.quantlab.stock;

import com.quantlab.stock.domain.ListingStatus;
import com.quantlab.stock.domain.MarketType;
import com.quantlab.stock.domain.Stock;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class StockFixture {

    public static Stock createStock() {
        return createStock("005930", "삼성전자");
    }

    public static Stock createStock(String stockCode, String stockName) {
        return Stock.of(stockCode, stockName, MarketType.KOSPI,
            ListingStatus.LISTED, "전기전자");
    }
}
