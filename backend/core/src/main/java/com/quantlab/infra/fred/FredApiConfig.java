package com.quantlab.infra.fred;

import java.net.http.HttpClient;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(FredApiProperties.class)
public class FredApiConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "Mozilla/5.0";

    private final FredApiProperties properties;

    /**
     * JDK HttpClient의 HTTP/2 협상이 fred.stlouisfed.org 앞단(CDN/WAF로
     * 추정)에서 "Received RST_STREAM: Internal error"로 즉시 거부돼
     * 기동 직후부터 반복 실패하는 현상이 실서버에서 확인됨(2026-07-18,
     * 66회 연속) - PythonEngineConfig와 동일하게 HTTP/1.1로 고정해 이
     * HTTP/2 스트림 자체를 만들지 않도록 우회. User-Agent 미설정 시
     * 요청을 거부하는 KIND(kind.krx.co.kr)와 동일한 패턴일 가능성도
     * 있어(infra/kind/KindApiConfig 참고) 기본 User-Agent도 함께 설정.
     */
    @Bean
    public RestClient fredRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("User-Agent", USER_AGENT)
            .requestFactory(requestFactory)
            .build();
    }
}
