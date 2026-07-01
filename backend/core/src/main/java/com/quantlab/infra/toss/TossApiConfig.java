package com.quantlab.infra.toss;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TossApiProperties.class)
public class TossApiConfig {

    private final TossApiProperties properties;

    @Bean
    public RestClient tossRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("accept", "application/json")
            .build();
    }
}
