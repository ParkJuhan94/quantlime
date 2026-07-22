package com.quantlime.infra.toss;

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
@EnableConfigurationProperties(TossApiProperties.class)
public class TossApiConfig {

    private final TossApiProperties properties;

    @PostConstruct
    void validateCredentials() {
        if (!StringUtils.hasText(properties.getClientId())
            || !StringUtils.hasText(properties.getClientSecret())) {
            log.warn("토스증권 API 크리덴셜 미설정: TOSS_CLIENT_ID/TOSS_CLIENT_SECRET 확인 필요 "
                + "(.env 파일이 실행 working directory에서 로딩되는지 확인)");
        }
    }

    @Bean
    public RestClient tossRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("accept", "application/json")
            .build();
    }
}
