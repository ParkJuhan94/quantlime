package com.quantlime.infra.kis;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(KisApiProperties.class)
public class KisApiConfig {

    private final KisApiProperties properties;

    /**
     * 해외주식 종목정보 마스터파일(.mst.cod.zip) 다운로드 전용 - 인증 불필요한
     * 정적 파일 CDN(2026-07-29 - 시세 조회 관련 RestClient/토큰 발급은 Toss로
     * 전량 이관되며 함께 삭제됨, KisApiErrorCode 참고).
     */
    @Bean
    public RestClient kisMasterFileRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getMasterFileBaseUrl())
            .build();
    }
}
