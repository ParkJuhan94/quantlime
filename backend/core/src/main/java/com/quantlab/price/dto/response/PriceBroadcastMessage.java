package com.quantlab.price.dto.response;

/**
 * WebSocket(/topic/price/{stockCode})으로 브로드캐스트되는 실시간 시세
 * 메시지이자, Redis 캐시(price:current:{stockCode})에 저장되는 스냅샷 값.
 *
 * <p>거래량(volume)은 의도적으로 제외한다. 폴링 소스인 Toss
 * 현재가 API(getCurrentPrices)는 거래량을 주지 않고, 이를 얻으려면 더
 * 빡빡한 레이트리밋 그룹(MARKET_DATA_CHART)의 캔들 API를 매 틱마다
 * 추가로 호출해야 해 실익 대비 비용이 크다.
 */
public record PriceBroadcastMessage(
    String stockCode,
    Long currentPrice,
    Double changeRate,
    String timestamp
) {
}
