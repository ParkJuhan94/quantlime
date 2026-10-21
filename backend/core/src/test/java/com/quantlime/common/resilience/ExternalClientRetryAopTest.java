package com.quantlime.common.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.upbit.UpbitApiClient;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 외부 클라이언트에 붙인 {@code @Retry}가 실제 AOP 프록시를 통해 동작하는지 검증한다 -
 * 어노테이션만 붙이고 aspect가 안 걸려 있으면 아무 일도 안 일어나서 단위 테스트로는 못 잡는다.
 * 클라이언트 구현 하나({@link UpbitApiClient})로 공통 설정(기본 {@code TransientFailurePredicate})을
 * 대표 검증하고, 실제 {@code application.yml} 값 자체는 bootRun 후 actuator 메트릭으로 확인한다.
 */
@Tag("unit")
class ExternalClientRetryAopTest {

    private static final String TICKER_URI = "https://upbit.test/v1/ticker?markets=KRW-BTC";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            AopAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class,
            BulkheadAutoConfiguration.class,
            RetryAutoConfiguration.class))
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "resilience4j.retry.configs.default.max-attempts=3",
            "resilience4j.retry.configs.default.wait-duration=10ms",
            "resilience4j.retry.configs.default.retry-exception-predicate="
                + "com.quantlime.common.resilience.TransientFailurePredicate",
            "resilience4j.retry.instances.upbit.base-config=default");

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        RestClient.Builder upbitRestClientBuilder() {
            return RestClient.builder().baseUrl("https://upbit.test");
        }

        @Bean
        MockRestServiceServer mockServer(RestClient.Builder upbitRestClientBuilder) {
            return MockRestServiceServer.bindTo(upbitRestClientBuilder).build();
        }

        @Bean
        UpbitApiClient upbitApiClient(RestClient.Builder upbitRestClientBuilder, MockRestServiceServer mockServer) {
            // mockServer 빈을 인자로 받아 빌더 바인딩이 build()보다 먼저 일어나게 한다
            return new UpbitApiClient(upbitRestClientBuilder.build());
        }
    }

    @Test
    @DisplayName("[5xx가 두 번 나도 세 번째에 성공하면 호출부는 예외 없이 결과를 받는다]")
    void retry_serverErrorTwice_thenSucceeds() {
        runner.run(context -> {
            // given
            MockRestServiceServer server = context.getBean(MockRestServiceServer.class);
            server.expect(requestTo(TICKER_URI)).andRespond(withServerError());
            server.expect(requestTo(TICKER_URI)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
            server.expect(requestTo(TICKER_URI)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            // when
            var result = context.getBean(UpbitApiClient.class).getTicker("KRW-BTC");

            // then: 세 번 모두 소비돼야 verify가 통과한다
            assertThat(result).isEmpty();
            server.verify();
        });
    }

    @Test
    @DisplayName("[connect 실패는 재시도한다]")
    void retry_connectFailure_thenSucceeds() {
        runner.run(context -> {
            // given
            MockRestServiceServer server = context.getBean(MockRestServiceServer.class);
            server.expect(requestTo(TICKER_URI)).andRespond(withException(new ConnectException("refused")));
            server.expect(requestTo(TICKER_URI)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            // when
            var result = context.getBean(UpbitApiClient.class).getTicker("KRW-BTC");

            // then
            assertThat(result).isEmpty();
            server.verify();
        });
    }

    @Test
    @DisplayName("[5xx가 계속되면 최대 3회 시도 후 ExternalApiException으로 실패한다]")
    void retry_serverErrorForever_givesUpAfterMaxAttempts() {
        runner.run(context -> {
            // given: 응답을 정확히 3건만 준비 - 4번째 요청이 나가면 MockRestServiceServer가 실패시킨다
            MockRestServiceServer server = context.getBean(MockRestServiceServer.class);
            for (int i = 0; i < 3; i++) {
                server.expect(requestTo(TICKER_URI)).andRespond(withServerError());
            }

            // when & then
            assertThatThrownBy(() -> context.getBean(UpbitApiClient.class).getTicker("KRW-BTC"))
                .isInstanceOf(ExternalApiException.class);
            server.verify();
        });
    }

    @Test
    @DisplayName("[4xx는 재시도하지 않고 한 번만 호출한다]")
    void noRetry_clientError() {
        runner.run(context -> {
            // given
            MockRestServiceServer server = context.getBean(MockRestServiceServer.class);
            server.expect(requestTo(TICKER_URI)).andRespond(withStatus(HttpStatus.NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> context.getBean(UpbitApiClient.class).getTicker("KRW-BTC"))
                .isInstanceOf(ExternalApiException.class);
            server.verify();
        });
    }

    @Test
    @DisplayName("[read timeout은 재시도하지 않고 한 번만 호출한다]")
    void noRetry_readTimeout() {
        runner.run(context -> {
            // given
            MockRestServiceServer server = context.getBean(MockRestServiceServer.class);
            server.expect(requestTo(TICKER_URI))
                .andRespond(withException(new HttpTimeoutException("request timed out")));

            // when & then
            assertThatThrownBy(() -> context.getBean(UpbitApiClient.class).getTicker("KRW-BTC"))
                .isInstanceOf(ExternalApiException.class);
            server.verify();
        });
    }
}
