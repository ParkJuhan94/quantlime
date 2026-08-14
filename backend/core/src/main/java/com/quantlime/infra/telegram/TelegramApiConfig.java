package com.quantlime.infra.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TelegramApiProperties.class)
public class TelegramApiConfig {

    private final TelegramApiProperties properties;

    @Bean
    public RestClient telegramRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("User-Agent", properties.getUserAgent())
            .defaultHeader("Accept-Language", "ko,en;q=0.8")
            .build();
    }
}
