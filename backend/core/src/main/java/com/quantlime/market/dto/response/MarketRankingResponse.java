package com.quantlime.market.dto.response;

/**
 * {@code currency}/{@code tradingVolume}/{@code tradingAmount}는 2026-07-29
 * Toss `/api/v1/rankings` 연동으로 추가됐다(ROADMAP #2c 거래대금 실시간
 * 랭킹). 국내 관심종목만 보기(자체 계산 경로, {@code DomesticMarketRankingCache})
 * 결과에는 이 세 필드가 항상 null - 거래량 자체를 안 다루는 경로라서다
 * (PriceSnapshot 주석 참고). {@code currentPrice}는 해외 소수점 가격을
 * 담기 위해 Long에서 Double로 확대됨(2026-07-29). {@code logoUrl}은
 * 로컬 stock 테이블에 있는 종목만 채워지고(StockMapper.toLogoUrl 참고),
 * 없으면 null - 심볼만으로는 나스닥 접미사(.O) 여부를 알 수 없어 프론트가
 * 임의로 조립하지 않는다(2026-07-31). {@code detailAvailable}은 로컬
 * stock 테이블에 이 종목이 있는지 여부(2026-08-01 추가) - {@code logoUrl
 * == null}과는 의미가 다르다(로고는 종목이 있어도 로고 자산이 없으면
 * null일 수 있음). 국내 scope는 이미 로컬 마스터에 없는 심볼을 필터링해서
 * 빼므로 항상 true, 해외 scope만 실질적으로 false가 나올 수 있다 -
 * 프론트가 이 값으로 상세페이지 진입/관심종목 등록을 막는다(둘 다
 * 내부적으로 stockCode 조회에 실패해 404가 나므로).
 */
public record MarketRankingResponse(
    String stockCode,
    String stockName,
    String sector,
    Double currentPrice,
    Double changeRate,
    String currency,
    Double tradingVolume,
    Double tradingAmount,
    String logoUrl,
    boolean detailAvailable
) {
}
