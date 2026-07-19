package com.quantlab.price.dto;

/**
 * 종목별 누적 거래대금(종가×거래량 합) 집계 결과. 백테스트 국내 유니버스
 * 선정(거래대금 상위 500)의 정렬 기준으로만 쓰인다 - 실제 거래대금(체결가
 * 기준)의 근사치이며, 토스 캔들 응답이 거래대금 필드를 직접 제공하지
 * 않아 종가×거래량으로 proxy한다(CLAUDE.md 스코어링 백테스트 계획 참고).
 */
public record StockTradingValue(String stockCode, Long tradingValue) {
}
