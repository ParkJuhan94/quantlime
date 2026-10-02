package com.quantlime.price.dto.response;

/** 미국 주식 등 가격제한이 없는 시장은 upperLimitPrice/lowerLimitPrice가 null이다. */
public record PriceLimitResponse(
    Double upperLimitPrice,
    Double lowerLimitPrice,
    String currency
) {
}
