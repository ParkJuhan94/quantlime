package com.quantlime.infra.toss.realtime;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;

/** 웹소켓 체결 채널의 시장 구분. 한 시장의 채널에 다른 시장 종목을 넣으면 그 항목만 거부된다. */
@Getter
@RequiredArgsConstructor
public enum TossRealtimeMarket {
    KR("kr"),
    US("us");

    private final String code;

    public String tradeChannel() {
        return "trade:" + code;
    }

    @Nullable
    public static TossRealtimeMarket fromCode(String code) {
        for (TossRealtimeMarket market : values()) {
            if (market.code.equals(code)) {
                return market;
            }
        }
        return null;
    }
}
