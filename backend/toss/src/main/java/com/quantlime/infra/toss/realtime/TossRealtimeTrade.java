package com.quantlime.infra.toss.realtime;

import java.time.OffsetDateTime;

/** 체결 프레임 한 건. 누적 거래량·매수/매도 구분은 스펙상 제공되지 않는다. */
public record TossRealtimeTrade(
    TossRealtimeMarket market,
    String symbol,
    double price,
    double volume,
    OffsetDateTime timestamp
) {
}
