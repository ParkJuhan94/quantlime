package com.quantlime.price.dto;

/**
 * 종목별 유동성 집계 결과(최근 20거래일 일평균 거래대금·거래량 0인 날 수).
 * 국내/해외 가격 테이블 각각에서 같은 형태로 집계되므로 하나의 DTO를
 * 공유한다({@link DomesticStockTradingValue}/{@link OverseasStockTradingValue}가
 * 가격 타입(Long/Double) 차이로 분리된 것과 달리, 여기서는 평균(avg)이라
 * 양쪽 다 자연히 소수(Double)가 된다).
 */
public record LiquiditySnapshot(String stockCode, Double avgTradingValue, Long zeroVolumeDays) {
}
