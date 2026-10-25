package com.quantlime.infra.python;

import com.quantlime.common.config.HttpClientFactorySupport;
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
    // P4(AI 요약) 도입 전에는 10초였다 - 실제 자막(4.6만자)으로 라이브 테스트해보니
    // Gemini 구조화 출력 호출 하나가 약 12초 걸려 그대로 컷당하는 걸 확인했다
    // (2026-07-29). score/backtest/transcribe 호출부는 전부 @Transactional 밖에서
    // 실행돼(ScoreService/BacktestService/*CollectionFacade 확인) 이 값을 늘려도
    // DB 트랜잭션을 더 오래 붙드는 부작용이 없어 공유 클라이언트 값 자체를 올렸다.
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    // 스코어 계산 전용(2026-10-26). 100종목 청크가 로컬 실측 2.1~2.4초, 팬아웃 단건은
    // 0.03초라 60초는 hang 때 비용만 키운다(서킷 예열 5콜 x 60초, half-open probe 3콜 x 60초).
    // 운영 CPU가 더 작고 백테스트와 경합하는 걸 감안해 약 6배 여유를 둔다.
    private static final Duration SCORE_READ_TIMEOUT = Duration.ofSeconds(15);

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
        return build(READ_TIMEOUT);
    }

    /** 백테스트(횡단면 백테스트는 60초를 넘긴 기록이 있음)와 타임아웃을 나누기 위한 스코어 전용 클라이언트. */
    @Bean
    public RestClient pythonEngineScoreRestClient() {
        return build(SCORE_READ_TIMEOUT);
    }

    private RestClient build(Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = HttpClientFactorySupport.create(
            CONNECT_TIMEOUT, readTimeout, HttpClient.Version.HTTP_1_1);

        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("accept", "application/json")
            .requestFactory(requestFactory)
            .build();
    }
}
