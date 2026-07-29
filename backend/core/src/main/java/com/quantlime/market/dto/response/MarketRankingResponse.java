package com.quantlime.market.dto.response;

/**
 * {@code currency}/{@code tradingVolume}/{@code tradingAmount}는 2026-07-29
 * Toss `/api/v1/rankings` 연동으로 추가됐다(ROADMAP #2c 거래대금 실시간
 * 랭킹). 국내 관심종목만 보기(자체 계산 경로, {@code MarketRankingCache})
 * 결과에는 이 세 필드가 항상 null - 거래량 자체를 안 다루는 경로라서다
 * (PriceSnapshot 주석 참고). {@code currentPrice}는 해외 소수점 가격을
 * 담기 위해 Long에서 Double로 확대됨(2026-07-29).
 */
public record MarketRankingResponse(
    String stockCode,
    String stockName,
    String sector,
    Double currentPrice,
    Double changeRate,
    String currency,
    Double tradingVolume,
    Double tradingAmount
) {
}
