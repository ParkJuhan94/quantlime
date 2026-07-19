package com.quantlab.price.dto;

/**
 * 해외 종목별 누적 거래대금(종가×거래량 합) 집계 결과. 국내
 * {@link StockTradingValue}와 동일한 목적이나, 해외 가격은 소수(Double)라
 * 값 타입만 다르게 별도로 둔다.
 */
public record OverseasStockTradingValue(String stockCode, Double tradingValue) {
}
