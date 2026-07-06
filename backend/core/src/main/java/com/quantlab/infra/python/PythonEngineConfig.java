package com.quantlab.infra.python;

import java.net.http.HttpClient;
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

    private final PythonEngineProperties properties;

    /**
     * JDK HttpClient는 평문(http://) 연결에서도 기본적으로 HTTP/2 cleartext(h2c)
     * 업그레이드를 시도한다. uvicorn(h11)은 이를 지원하지 않아 "Unsupported
     * upgrade request"로 응답하며 그 뒤에 이어지는 실제 POST 바디가 비어버리는
     * 현상이 발생했다(Python 쪽에서 "body: Field required"로 관측됨). 로컬
     * uvicorn과의 통신은 HTTP/1.1로 고정해 이 업그레이드 협상 자체를 건너뛴다.
     */
    @Bean
    public RestClient pythonEngineRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("accept", "application/json")
            .requestFactory(new JdkClientHttpRequestFactory(httpClient))
            .build();
    }
}
