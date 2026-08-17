package com.quantlime.infra.youtube;

import com.quantlime.common.config.HttpClientFactorySupport;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
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

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

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
            .requestFactory(HttpClientFactorySupport.create(CONNECT_TIMEOUT, READ_TIMEOUT))
            .build();
    }
}
