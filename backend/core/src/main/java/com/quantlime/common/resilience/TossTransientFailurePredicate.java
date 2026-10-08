package com.quantlime.common.resilience;

/**
 * {@link TransientFailurePredicate}의 Toss 전용 변형 - 429를 재시도 대상에서 뺀다.
 *
 * <p>Toss 429는 {@code TossApiClient}가 엔드포인트별로 직접 다룬다: 저빈도
 * 엔드포인트는 Retry-After 헤더 기반 대기 후 재시도({@code withRateLimitRetry}),
 * 시세 스윕처럼 고빈도 경로는 다음 틱이 곧 재시도라 즉시 실패시킨다. 여기서 429를
 * 또 재시도하면 이중 재시도가 되고, 특히 후자의 fail-fast 설계가 깨진다.
 */
public class TossTransientFailurePredicate extends TransientFailurePredicate {

    public TossTransientFailurePredicate() {
        super(false);
    }
}
