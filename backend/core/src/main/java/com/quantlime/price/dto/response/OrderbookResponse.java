package com.quantlime.price.dto.response;

import java.util.List;
import org.springframework.lang.Nullable;

/** 호가 - asks는 낮은 가격순, bids는 높은 가격순(토스 응답 순서 그대로). */
public record OrderbookResponse(
    String timestamp,
    String currency,
    List<Level> asks,
    List<Level> bids
) {

    public record Level(@Nullable Double price, @Nullable Double volume) {
    }
}
