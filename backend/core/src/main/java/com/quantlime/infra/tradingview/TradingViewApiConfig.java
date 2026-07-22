package com.quantlime.infra.tradingview;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TradingViewApiProperties.class)
public class TradingViewApiConfig {

    private final TradingViewApiProperties properties;

    @Bean
    public RestClient tradingViewRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build();
    }
}
