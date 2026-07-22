package com.quantlime.infra.oauth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OAuthProperties.class)
public class OAuthClientConfig {

    @Bean
    public RestClient oAuthRestClient() {
        return RestClient.builder()
            .defaultHeader("accept", "application/json")
            .build();
    }
}
