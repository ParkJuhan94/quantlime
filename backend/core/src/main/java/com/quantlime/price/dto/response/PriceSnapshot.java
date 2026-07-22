package com.quantlime.price.dto.response;

/**
 * 종목 현재가 스냅샷. {@code MarketPriceSweepScheduler}가 전종목 시세를
 * 이 타입으로 만들어 Redis 캐시(price:current:{stockCode})에 저장하는 게
 * 본체이고, {@code WatchlistPriceRelayScheduler}가 관심종목만 골라 이
 * 값을 그대로 WebSocket(/topic/price/{stockCode})으로 중계하는 것과
 * {@code StockPriceService}의 read-through 캐시 조회는 둘 다 이 저장된
 * 값을 재사용하는 소비자일 뿐이다 - "브로드캐스트 메시지"가 아니라
 * "가격 스냅샷"이 본질이라 이름도 그에 맞춰 `PriceBroadcastMessage`에서
 * 변경함(2026-07-16).
 *
 * <p>거래량(volume)은 의도적으로 제외한다. 폴링 소스인 Toss
 * 현재가 API(getCurrentPrices)는 거래량을 주지 않고, 이를 얻으려면 더
 * 빡빡한 레이트리밋 그룹(MARKET_DATA_CHART)의 캔들 API를 매 틱마다
 * 추가로 호출해야 해 실익 대비 비용이 크다.
 */
public record PriceSnapshot(
    String stockCode,
    Long currentPrice,
    Double changeRate,
    String timestamp
) {
}
