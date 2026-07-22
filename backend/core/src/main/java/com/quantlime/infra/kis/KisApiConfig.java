package com.quantlime.infra.kis;

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
@EnableConfigurationProperties(KisApiProperties.class)
public class KisApiConfig {

    private final KisApiProperties properties;

    @PostConstruct
    void validateCredentials() {
        if (!StringUtils.hasText(properties.getAppKey())
            || !StringUtils.hasText(properties.getAppSecret())) {
            log.warn("한국투자증권(KIS) API 크리덴셜 미설정: KIS_APP_KEY/KIS_APP_SECRET 확인 필요 "
                + "(.env 파일이 실행 working directory에서 로딩되는지 확인)");
        }
    }

    @Bean
    public RestClient kisRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("content-type", "application/json; charset=utf-8")
            .build();
    }

    /**
     * 해외주식 종목정보 마스터파일(.mst.cod.zip) 다운로드 전용 - KIS API
     * 서버(kisRestClient)와 별도의 정적 파일 CDN 호스트를 사용한다.
     */
    @Bean
    public RestClient kisMasterFileRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getMasterFileBaseUrl())
            .build();
    }
}
