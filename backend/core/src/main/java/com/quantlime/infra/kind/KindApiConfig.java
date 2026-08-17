package com.quantlime.infra.kind;

import com.quantlime.common.config.HttpClientFactorySupport;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(KindApiProperties.class)
public class KindApiConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final KindApiProperties properties;

    @Bean
    public RestClient kindRestClient() {
        // KIND는 정식 공개 API가 아니라 브라우저 다운로드 페이지라
        // User-Agent가 없으면 요청 자체를 거부한다.
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("User-Agent", "Mozilla/5.0")
            .requestFactory(HttpClientFactorySupport.create(CONNECT_TIMEOUT, READ_TIMEOUT))
            .build();
    }
}
