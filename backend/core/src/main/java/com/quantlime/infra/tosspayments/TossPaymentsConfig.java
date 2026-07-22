package com.quantlime.infra.tosspayments;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
@EnableConfigurationProperties(TossPaymentsProperties.class)
public class TossPaymentsConfig {

    private final TossPaymentsProperties properties;

    @PostConstruct
    void validateCredentials() {
        if (!StringUtils.hasText(properties.getSecretKey())) {
            log.warn("토스페이먼츠 시크릿키 미설정: TOSS_PAYMENTS_SECRET_KEY 확인 필요 "
                + "(.env 파일이 실행 working directory에서 로딩되는지 확인)");
        }
    }

    // 토스페이먼츠는 시크릿키를 Basic Auth의 아이디로, 비밀번호는 빈
    // 문자열로 사용한다(토스증권 Open API의 OAuth2 client_credentials와는
    // 별개 인증 체계 - 토큰 발급/캐싱이 필요 없다).
    @Bean
    public RestClient tossPaymentsRestClient() {
        String basicAuth = Base64.getEncoder().encodeToString(
            (properties.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8));
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("Authorization", "Basic " + basicAuth)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
