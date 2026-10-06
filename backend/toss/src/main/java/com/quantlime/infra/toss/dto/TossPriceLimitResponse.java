package com.quantlime.infra.toss.dto;

public record TossPriceLimitResponse(
    PriceLimit result
) {

    // 미국 주식 등 가격제한이 없는 시장에서는 상/하한가가 null이다.
    public record PriceLimit(
        String timestamp,
        String upperLimitPrice,
        String lowerLimitPrice,
        String currency
    ) {
    }
}
