package com.quantlab.market.dto.response;

public record MarketIndexResponse(
    Double usdKrwRate,
    String usdKrwChangeType,
    Long bitcoinPriceKrw,
    Double bitcoinChangeRate
) {
}
