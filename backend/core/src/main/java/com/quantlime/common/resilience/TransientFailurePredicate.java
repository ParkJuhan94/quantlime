package com.quantlime.common.resilience;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.function.Predicate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Resilience4j {@code @Retry}가 재시도할 "일시적 장애"만 골라내는 판별자.
 *
 * <p>재시도 대상은 서버가 요청을 못 받았거나 명시적으로 거절한 경우뿐이다 -
 * connect 실패/connect timeout, 5xx, 429. <b>read timeout은 제외한다</b>:
 * 서버가 요청을 받아 처리 중일 수 있어 과부하를 더 키우고, 시도 횟수만큼
 * 호출 스레드(특히 캐시 갱신에 진입한 사용자 요청 스레드)의 최악 지연이
 * 늘어난다. 4xx(429 제외)와 응답 파싱 실패는 재시도해도 같은 결과라 제외한다.
 *
 * <p>이 프로젝트의 클라이언트는 실패를 {@code ExternalApiException(cause)}로
 * 감싸므로 cause 체인을 따라 내려가며 판정한다. 서킷 open
 * ({@code CallNotPermittedException})·벌크헤드 포화는 체인에 해당 타입이
 * 없어 자연스럽게 false가 된다(재시도가 서킷을 두드리는 걸 막는다).
 *
 * <p>resilience4j의 {@code retry-exception-predicate} 설정은 no-arg 생성자로
 * 리플렉션 인스턴스화하므로(스프링 빈 아님), 429 처리 여부가 다른 변형은
 * 서브클래스({@link TossTransientFailurePredicate})로 분리한다.
 */
public class TransientFailurePredicate implements Predicate<Throwable> {

    private final boolean retryOnRateLimit;

    public TransientFailurePredicate() {
        this(true);
    }

    protected TransientFailurePredicate(boolean retryOnRateLimit) {
        this.retryOnRateLimit = retryOnRateLimit;
    }

    @Override
    public boolean test(Throwable throwable) {
        // 순환 cause 방어용 상한 - 실제 체인은 3~4단계.
        int depth = 0;
        for (Throwable t = throwable; t != null && depth < 10; t = t.getCause(), depth++) {
            if (t instanceof HttpClientErrorException.TooManyRequests) {
                return retryOnRateLimit;
            }
            if (t instanceof HttpServerErrorException
                || t instanceof ConnectException
                || t instanceof HttpConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
