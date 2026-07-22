package com.quantlime.infra.naver;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(NaverFinanceApiProperties.class)
public class NaverFinanceApiConfig {

    private final NaverFinanceApiProperties properties;

    @Bean
    public RestClient naverFinanceRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            // 비공식 모바일 API라 User-Agent 없이 호출하면 차단될 수 있어
            // 일반 모바일 브라우저처럼 보이는 UA를 고정으로 붙인다.
            .defaultHeader("User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15")
            .build();
    }

    // 분봉 차트 전용 - api.stock.naver.com은 m.stock.naver.com과 별개
    // 호스트라(NaverFinanceApiProperties 참고) RestClient도 분리한다.
    @Bean
    public RestClient naverFinanceChartRestClient() {
        return RestClient.builder()
            .baseUrl(properties.getChartBaseUrl())
            .defaultHeader("User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15")
            .build();
    }
}
