package com.quantlime.event.market;

/** 전종목 가격·스코어 fan-out(2026-09-24)이 쓰는 Kafka 토픽 이름. */
public final class MarketTopics {

    public static final String PRICE_REFRESH_REQUESTED = "price.refresh.requested";

    private MarketTopics() {
    }
}
