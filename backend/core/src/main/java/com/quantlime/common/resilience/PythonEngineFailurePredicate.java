package com.quantlime.common.resilience;

import com.quantlime.common.exception.ExternalApiException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.util.Set;
import java.util.function.Predicate;

/**
 * {@link RateLimitAwareFailurePredicate}와 동일한 이유의 quant-engine 버전 -
 * Gemini 분당/일일 한도에 걸린 것(PYE_004/PYE_006)과 자체 RateLimiter
 * 거절(RequestNotPermitted)은 "quant-engine이 죽었다"는 신호가 아니라
 * "우리가 스스로 속도를 줄이고 있다"는 신호라 quant-engine
 * CircuitBreaker(score/backtest 등과 공유)의 실패 집계에서 제외한다 -
 * 안 그러면 이 자체 쿼터 게이트가 스코어/백테스트 호출까지 연쇄로 막아버릴
 * 수 있다(2026-09-13 자막 500 연쇄 인시던트와 같은 종류의 문제).
 *
 * <p>resilience4j가 리플렉션(no-arg 생성자)으로 인스턴스화하므로 Spring
 * 빈이 아니다 - RateLimitAwareFailurePredicate와 동일한 제약.
 */
public class PythonEngineFailurePredicate implements Predicate<Throwable> {

    private static final Set<String> SELF_IMPOSED_THROTTLE_CODES = Set.of(
        "PYE_004", // SUMMARY_RATE_LIMIT_EXCEEDED
        "PYE_006"  // SUMMARY_DAILY_QUOTA_EXCEEDED
    );

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof RequestNotPermitted) {
            return false;
        }
        if (throwable instanceof ExternalApiException externalApiException) {
            return !SELF_IMPOSED_THROTTLE_CODES.contains(externalApiException.getCode());
        }
        return true;
    }
}
