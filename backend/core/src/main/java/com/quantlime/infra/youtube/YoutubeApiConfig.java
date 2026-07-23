package com.quantlime.infra.youtube;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(YoutubeApiProperties.class)
public class YoutubeApiConfig {

    private final YoutubeApiProperties properties;

    @PostConstruct
    void validateCredentials() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            log.warn("유튜브 Data API 키 미설정: YOUTUBE_API_KEY 확인 필요");
        }
    }

    @Bean
    public RestClient youtubeRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("accept", "application/json")
            .build();
    }
}
