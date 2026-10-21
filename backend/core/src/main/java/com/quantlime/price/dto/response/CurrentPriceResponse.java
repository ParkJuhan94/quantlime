package com.quantlime.price.dto.response;

import org.springframework.lang.Nullable;

/**
 * {@code price}는 2026-07-29 해외(USD) 실시간가 지원을 위해 Long에서
 * Double로 확대됐다.
 */
public record CurrentPriceResponse(
    String stockCode,
    @Nullable Double price,
    @Nullable Double changeRate,
    @Nullable String currency,
    @Nullable String timestamp
) {
}
