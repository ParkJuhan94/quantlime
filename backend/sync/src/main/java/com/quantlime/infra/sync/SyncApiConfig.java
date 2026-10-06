package com.quantlime.infra.sync;

import com.quantlime.common.config.HttpClientFactorySupport;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(SyncProperties.class)
public class SyncApiConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final SyncProperties properties;

    // prodApiBase가 비어있으면(운영 자신, 또는 동기화를 설정 안 한 로컬)
    // baseUrl이 빈 문자열인 채로 빈만 만들어진다 - 실제로 호출되는 일이
    // 없으므로(LocalTranscriptSyncService가 호출 전에 설정 여부를 먼저
    // 확인) 문제되지 않는다.
    @Bean
    public RestClient syncRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getProdApiBase() != null ? properties.getProdApiBase() : "")
            .requestFactory(HttpClientFactorySupport.create(CONNECT_TIMEOUT, READ_TIMEOUT))
            .build();
    }
}
