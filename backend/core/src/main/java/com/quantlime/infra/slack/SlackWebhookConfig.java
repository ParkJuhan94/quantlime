package com.quantlime.infra.slack;

import com.quantlime.common.config.HttpClientFactorySupport;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SlackWebhookProperties.class)
public class SlackWebhookConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    // 이전엔 SlackWebhookClient가 RestClient.create()로 직접 만들어 썼는데
    // (타임아웃 없음), 다른 외부 클라이언트와 동일하게 빈으로 분리해 타임아웃을
    // 명시한다(2026-08-17).
    @Bean
    public RestClient slackRestClient() {
        return RestClient.builder()
            .requestFactory(HttpClientFactorySupport.create(CONNECT_TIMEOUT, READ_TIMEOUT))
            .build();
    }
}
