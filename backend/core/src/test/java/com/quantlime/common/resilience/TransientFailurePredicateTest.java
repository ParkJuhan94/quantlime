package com.quantlime.common.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.upbit.exception.UpbitApiErrorCode;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Tag("unit")
class TransientFailurePredicateTest {

    private final TransientFailurePredicate predicate = new TransientFailurePredicate();
    private final TransientFailurePredicate tossPredicate = new TossTransientFailurePredicate();

    private ExternalApiException wrap(Throwable cause) {
        return new ExternalApiException(UpbitApiErrorCode.TICKER_INQUIRY_FAILED, cause);
    }

    @Test
    @DisplayName("[5xx 응답은 재시도 대상이다]")
    void test_serverError_isTransient() {
        // given
        Throwable failure = wrap(HttpServerErrorException.create(
            HttpStatus.SERVICE_UNAVAILABLE, "unavailable", HttpHeaders.EMPTY, new byte[0], null));

        // when & then
        assertThat(predicate.test(failure)).isTrue();
    }

    @Test
    @DisplayName("[connect 실패와 connect timeout은 ResourceAccessException으로 한 번 더 감싸져도 재시도 대상이다]")
    void test_connectFailure_isTransient() {
        // given
        Throwable refused = wrap(new ResourceAccessException("I/O error", new ConnectException("refused")));
        Throwable connectTimeout = wrap(new ResourceAccessException(
            "I/O error", new HttpConnectTimeoutException("connect timed out")));

        // when & then
        assertThat(predicate.test(refused)).isTrue();
        assertThat(predicate.test(connectTimeout)).isTrue();
    }

    @Test
    @DisplayName("[read timeout은 재시도하지 않는다 - 서버가 처리 중일 수 있고 호출 스레드 지연만 늘어난다]")
    void test_readTimeout_isNotTransient() {
        // given: HttpConnectTimeoutException의 부모 타입(HttpTimeoutException) 자체는 read timeout 쪽이다
        Throwable readTimeout = wrap(new ResourceAccessException(
            "I/O error", new HttpTimeoutException("request timed out")));

        // when & then
        assertThat(predicate.test(readTimeout)).isFalse();
    }

    @Test
    @DisplayName("[429는 기본 predicate에선 재시도하지만 Toss 변형에선 제외한다 - Toss는 클라이언트가 직접 처리]")
    void test_rateLimit_differsBetweenDefaultAndToss() {
        // given
        Throwable rateLimited = wrap(HttpClientErrorException.create(
            HttpStatus.TOO_MANY_REQUESTS, "slow down", HttpHeaders.EMPTY, new byte[0], null));

        // when & then
        assertThat(predicate.test(rateLimited)).isTrue();
        assertThat(tossPredicate.test(rateLimited)).isFalse();
    }

    @Test
    @DisplayName("[Toss 변형도 5xx와 connect 실패는 재시도한다]")
    void test_tossPredicate_stillRetriesServerErrorAndConnectFailure() {
        // given
        Throwable serverError = wrap(HttpServerErrorException.create(
            HttpStatus.BAD_GATEWAY, "bad gateway", HttpHeaders.EMPTY, new byte[0], null));
        Throwable refused = wrap(new ResourceAccessException("I/O error", new ConnectException("refused")));

        // when & then
        assertThat(tossPredicate.test(serverError)).isTrue();
        assertThat(tossPredicate.test(refused)).isTrue();
    }

    @Test
    @DisplayName("[4xx(429 제외)·파싱 실패·원인 없는 예외는 재시도하지 않는다]")
    void test_clientErrorAndParseFailure_areNotTransient() {
        // given
        Throwable notFound = wrap(HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "not found", HttpHeaders.EMPTY, new byte[0], null));
        Throwable parseFailure = wrap(new IllegalStateException("마크업 변경 가능성"));
        Throwable noCause = new ExternalApiException(UpbitApiErrorCode.TICKER_INQUIRY_FAILED);

        // when & then
        assertThat(predicate.test(notFound)).isFalse();
        assertThat(predicate.test(parseFailure)).isFalse();
        assertThat(predicate.test(noCause)).isFalse();
    }
}
