package com.quantlime.event.retry;

/**
 * 모든 {@code @RetryableTopic} 컨슈머가 공유하는 재시도 백오프 값(운영 기본값 30s→90s→270s). 어노테이션
 * 속성에는 프로퍼티 플레이스홀더 표현식을 써서(2026-10-01), 테스트가 {@code kafka.retry.*}로 간격을
 * 밀리초 단위로 줄여 재시도→DLT 전 과정을 초 단위로 검증할 수 있게 한다. 운영에서는 프로퍼티를 정하지
 * 않으므로 기본값이 그대로 적용돼 동작은 이전과 같다.
 */
public final class RetryBackoff {

    public static final String DELAY_MS = "${kafka.retry.delay-ms:30000}";
    public static final String MULTIPLIER = "${kafka.retry.multiplier:3.0}";
    public static final String MAX_DELAY_MS = "${kafka.retry.max-delay-ms:270000}";

    private RetryBackoff() {
    }
}
