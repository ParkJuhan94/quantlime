package com.quantlime.infra.upbit;

import com.quantlime.common.config.HttpClientFactorySupport;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(UpbitApiProperties.class)
public class UpbitApiConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final UpbitApiProperties properties;

    @Bean
    public RestClient upbitRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("accept", "application/json")
            .requestFactory(HttpClientFactorySupport.create(CONNECT_TIMEOUT, READ_TIMEOUT))
            .build();
    }
}
