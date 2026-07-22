package com.quantlime.infra.tradingview;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "tradingview")
public class TradingViewApiProperties {

    private final String baseUrl;
}
