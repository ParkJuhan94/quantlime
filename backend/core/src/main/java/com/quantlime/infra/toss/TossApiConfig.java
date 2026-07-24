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

    /**
     * 토스 크리덴셜이 비어 있으면 기동 자체를 실패시킨다(fail-fast). 과거엔
     * WARN 로그 한 줄만 남기고 넘어갔는데, 그러면 앱이 빈 client_id/secret으로
     * 토스를 계속 호출해 {@code 400 {"error":"invalid_client"}}(gzip이라 로그엔
     * 깨진 바이너리로 찍힘)를 무한 반복하면서도 startup에는 아무 이상이 없어
     * 보여, 실제로 원인 파악에 이틀이 걸린 사고가 있었다(spring-dotenv가 IDE
     * Run Configuration의 working directory(리포 루트)에서 backend/.env를 못
     * 찾아 크리덴셜이 빈 값으로 폴백됐던 케이스). 없으면 즉시 명확히 터지게
     * 해서 이 실패 모드를 startup 시점으로 앞당긴다. 테스트(@ActiveProfiles
     * ("test"))는 application-test.yml이 더미 크리덴셜을 고정 제공한다.
     */
    @PostConstruct
    void validateCredentials() {
        if (!StringUtils.hasText(properties.getClientId())
            || !StringUtils.hasText(properties.getClientSecret())) {
            throw new IllegalStateException(
                "토스증권 API 크리덴셜(TOSS_CLIENT_ID/TOSS_CLIENT_SECRET)이 비어 있습니다. "
                    + "backend/.env가 실행 working directory에서 로딩되는지 확인하세요 "
                    + "(IDE 실행 시 Run Configuration의 working directory를 backend로 지정, "
                    + "또는 backend 디렉터리에서 bootRun 실행).");
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
