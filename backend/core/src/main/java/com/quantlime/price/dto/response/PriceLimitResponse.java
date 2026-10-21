package com.quantlime.price.dto.response;

import org.springframework.lang.Nullable;

/** 미국 주식 등 가격제한이 없는 시장은 upperLimitPrice/lowerLimitPrice가 null이다. */
public record PriceLimitResponse(
    @Nullable Double upperLimitPrice,
    @Nullable Double lowerLimitPrice,
    String currency
) {
}
