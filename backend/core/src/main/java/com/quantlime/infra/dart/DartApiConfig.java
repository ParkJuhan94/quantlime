package com.quantlime.infra.dart;

import com.quantlime.common.config.HttpClientFactorySupport;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(DartApiProperties.class)
public class DartApiConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    // corpCode.xml은 전체 상장법인(약 10만개 법인, 상장분은 그 일부) 목록을
    // ZIP으로 통째로 내려받는 벌크 다운로드라 일반 조회 API보다 넉넉하게 둔다.
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final DartApiProperties properties;

    @Bean
    public RestClient dartRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(HttpClientFactorySupport.create(CONNECT_TIMEOUT, READ_TIMEOUT))
            .build();
    }
}
