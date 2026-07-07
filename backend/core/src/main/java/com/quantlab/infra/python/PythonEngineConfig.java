package com.quantlab.infra.python;

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
@EnableConfigurationProperties(PythonEngineProperties.class)
public class PythonEngineConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final PythonEngineProperties properties;

    /**
     * JDK HttpClient는 평문(http://) 연결에서도 기본적으로 HTTP/2 cleartext(h2c)
     * 업그레이드를 시도한다. uvicorn(h11)은 이를 지원하지 않아 "Unsupported
     * upgrade request"로 응답하며 그 뒤에 이어지는 실제 POST 바디가 비어버리는
     * 현상이 발생했다(Python 쪽에서 "body: Field required"로 관측됨). 로컬
     * uvicorn과의 통신은 HTTP/1.1로 고정해 이 업그레이드 협상 자체를 건너뛴다.
     *
     * <p>connect/read 타임아웃을 명시하지 않으면 엔진이 느려지거나 응답을
     * 멈췄을 때 이 호출을 감싸는 트랜잭션이 DB 커넥션을 무한정 붙들게 된다.
     */
    @Bean
    public RestClient pythonEngineRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("accept", "application/json")
            .requestFactory(requestFactory)
            .build();
    }
}
