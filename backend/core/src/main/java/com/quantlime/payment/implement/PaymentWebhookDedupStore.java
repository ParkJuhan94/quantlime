package com.quantlime.payment.implement;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 결제 웹훅 처리 컨슈머의 멱등 체크(2026-09-30, 카프카 다도메인 확장
 * Phase 3) - Redis SETNX로 "이 payloadHash를 이미 처리했는지"만 표시한다.
 * {@link com.quantlime.common.lock.RedisLockService}처럼 소유권 토큰이
 * 필요 없다(락 해제가 없는 단순 마킹) - 한 번 SETNX에 성공하면 그걸로
 * "처리 시작"의 유일한 승자가 정해지고, 이후 값을 다시 지우거나 비교할
 * 일이 없다.
 *
 * <p>TTL(24시간)은 Toss가 웹훅을 재전송할 가능성이 있는 현실적인 창을
 * 넉넉히 덮기 위한 값이다 - 이 시점엔 실제 처리 로직 자체가 로깅뿐이라
 * (PaymentService.processWebhookEvent 주석 참고) TTL이 지나 마킹이
 * 사라져도 실질적 위험은 없다. 실제 상태 전이를 다루게 되면 DB 기반
 * 영구 원장으로 승격할지 재검토할 것.
 */
@Component
@RequiredArgsConstructor
public class PaymentWebhookDedupStore {

    private static final String KEY_PREFIX = "payment:webhook:dedup:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /**
     * @return true면 이번이 최초 처리(계속 진행), false면 이미 처리된
     *     payloadHash(중복이므로 스킵)
     */
    public boolean markProcessedIfAbsent(String payloadHash) {
        Boolean firstSeen = redisTemplate.opsForValue()
            .setIfAbsent(KEY_PREFIX + payloadHash, "1", TTL);
        return Boolean.TRUE.equals(firstSeen);
    }
}
