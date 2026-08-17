package com.quantlime.common.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * 외부 RestClient 빈들이 공유하는 connect/read 타임아웃 팩토리.
 *
 * <p>connect/read 타임아웃을 명시하지 않으면 JDK {@link HttpClient} 기본값
 * (둘 다 무제한)으로 떨어져, 외부 서버가 응답을 멈추면 호출 스레드가
 * 영구 블로킹된다 - {@code spring.task.scheduling.pool.size}가 작을 때는
 * 이 블로킹 하나가 다른 스케줄러 전체를 함께 멈춰 세운다(2026-08-17 감사에서
 * 실제로 12개 외부 클라이언트 중 quant-engine 하나만 이 값을 갖고 있었음을
 * 확인 - infra/python/PythonEngineConfig의 기존 위험 인지 주석 참고). 이
 * 헬퍼로 그 원칙을 나머지 외부 클라이언트에도 동일하게 적용한다.
 */
public final class HttpClientFactorySupport {

    private HttpClientFactorySupport() {
    }

    public static JdkClientHttpRequestFactory create(Duration connectTimeout, Duration readTimeout) {
        return create(connectTimeout, readTimeout, null);
    }

    /**
     * @param version JDK HttpClient는 평문(http://) 연결에서도 기본적으로 HTTP/2
     *                cleartext(h2c) 업그레이드를 시도한다. uvicorn(h11) 같은 h2c
     *                미지원 서버와 통신할 때는 {@link HttpClient.Version#HTTP_1_1}로
     *                고정해 이 업그레이드 협상 자체를 건너뛰어야 한다(quant-engine
     *                연동 중 실제로 겪은 문제 - PythonEngineConfig 참고). 협상이
     *                필요 없으면 {@code null}.
     */
    public static JdkClientHttpRequestFactory create(
        Duration connectTimeout, Duration readTimeout, HttpClient.Version version) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(connectTimeout);
        if (version != null) {
            builder.version(version);
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(builder.build());
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
