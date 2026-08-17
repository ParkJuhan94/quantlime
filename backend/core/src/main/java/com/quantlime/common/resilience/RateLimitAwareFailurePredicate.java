package com.quantlime.common.resilience;

import com.quantlime.common.exception.ExternalApiException;
import java.util.function.Predicate;

/**
 * 서킷브레이커 실패 집계에서 "레이트리밋(429)"을 제외하기 위한 판별자.
 *
 * <p>429는 "서버가 살아있는데 속도를 줄이라는 신호"라 서킷을 열면 복구를
 * 오히려 방해한다 - {@code TossApiClient.getDailyCandles}의 기존 동적 백오프
 * (Retry-After/X-RateLimit-Reset 헤더 기반)와 {@code TossTokenManager}의
 * 30초 쿨다운이 이미 이 신호를 별도로 다루고 있으므로, 서킷브레이커는 진짜
 * 장애(연결 실패/5xx/타임아웃)에만 반응하게 한다(2026-08-17, docs/RELIABILITY.md
 * 참고).
 *
 * <p>resilience4j의 {@code record-failure-predicate} 설정은 이 클래스를
 * 리플렉션으로(스프링 빈이 아니라 no-arg 생성자로) 인스턴스화하므로, 이
 * 프로젝트가 레이트리밋을 별도 예외 타입이 아니라
 * {@link ExternalApiException#getCode()} 문자열로만 구분하는 구조({@code
 * TossApiClient.java:89} 등)를 그대로 반영해 코드 문자열로 비교한다.
 */
public class RateLimitAwareFailurePredicate implements Predicate<Throwable> {

    // TossApiErrorCode.RATE_LIMIT_EXCEEDED.getCode()와 동일한 값 - enum을
    // 직접 참조하지 않는 이유는 클래스 상단 주석 참고.
    private static final String TOSS_RATE_LIMIT_CODE = "TOSS_004";

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof ExternalApiException externalApiException) {
            return !TOSS_RATE_LIMIT_CODE.equals(externalApiException.getCode());
        }
        return true;
    }
}
