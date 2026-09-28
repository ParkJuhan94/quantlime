package com.quantlime.common.lock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis SETNX 기반 분산락. 여러 인스턴스가 같은 스케줄 작업을 동시에
 * 실행하는 것을 막는 용도(현재는 단일 EC2 배포지만 스케일아웃 시에도
 * 그대로 유효) - Redisson 없이 이미 의존 중인 spring-data-redis만으로
 * 충분해 새 라이브러리를 추가하지 않았다.
 *
 * <p>락 값은 호출마다 새로 발급하는 토큰(UUID)이고, 해제는 Lua 스크립트로
 * "내가 쓴 토큰일 때만" 삭제한다 - 작업이 TTL을 넘겨 락이 만료되고 다른
 * 실행이 새로 락을 잡은 뒤에는, 원래 소유자가 뒤늦게 반환되며 부르는
 * unlock이 그 다른 실행의 락을 지우지 않게 하기 위함(2026-07-30 발견 -
 * 이전 구현은 값이 상수라 소유권을 구분할 수 없었음).
 *
 * <p>Redis 장애 시 예외를 흡수하는 폴백은 두지 않는다 - 이 락은 배치
 * 중복 실행 방지가 목적이라, 실패를 조용히 넘기고 "락 획득 성공"처럼
 * 동작하면 중복 실행을 막는다는 락의 존재 이유 자체가 무력화된다.
 * Redis가 죽으면 배치도 함께 fail-closed로 막히는 게 맞는 동작이다
 * (2026-08-17, PriceCacheStore와 다른 판단 - docs/00-sre/SRE.md "캐시" 절 참고).
 */
@Component
@RequiredArgsConstructor
public class RedisLockService {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]) "
            + "else return 0 end",
        Long.class);

    // "없으면 내가 잡는다, 이미 내가 잡고 있으면 TTL만 연장한다, 남이
    // 잡고 있으면 실패" 를 원자적으로 처리 - runExclusively(잡고 즉시 실행
    // 후 바로 해제)와 달리, 이건 호출자가 계속 살아있는 동안 리더 지위를
    // "유지"하는 용도(2026-09-25, PriceRelayLeaderGate 참고). 매 틱마다
    // 이 메서드만 부르면 되고, 별도 release는 없다 - 리더가 죽으면(재기동/
    // 크래시) TTL 만료로 자연히 다음 틱에 다른 인스턴스가 이어받는다.
    private static final DefaultRedisScript<Long> ACQUIRE_OR_RENEW_SCRIPT = new DefaultRedisScript<>(
        "local current = redis.call('get', KEYS[1]) "
            + "if current == false then "
            + "  redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2]) "
            + "  return 1 "
            + "elseif current == ARGV[1] then "
            + "  redis.call('pexpire', KEYS[1], ARGV[2]) "
            + "  return 1 "
            + "else "
            + "  return 0 "
            + "end",
        Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * key에 대한 락을 잡은 채로만 task를 실행한다. 이미 다른 실행이 락을
     * 쥐고 있으면 task를 실행하지 않고 빈 Optional을 반환한다(스케줄러
     * 중복 실행 스킵/관리자 수동 트리거 거절 양쪽에서 이 반환값으로
     * 분기하면 된다).
     */
    public <T> Optional<T> runExclusively(String key, Duration ttl, Supplier<T> task) {
        String token = UUID.randomUUID().toString();
        if (!tryLock(key, ttl, token)) {
            return Optional.empty();
        }
        try {
            return Optional.of(task.get());
        } finally {
            releaseIfOwned(key, token);
        }
    }

    private boolean tryLock(String key, Duration ttl, String token) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    private void releaseIfOwned(String key, String token) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
    }

    /**
     * key에 대한 리더 지위를 "획득 또는 연장"한다. token이 고정값(호출자가
     * 매번 같은 값을 넘김)이라는 게 {@link #runExclusively}와의 핵심
     * 차이 - 매 호출이 새 UUID를 발급하는 runExclusively와 달리, 이
     * 메서드는 "나(=token)"라는 정체성이 호출 간에 유지돼야 "내가 이미
     * 잡고 있으니 연장"을 Redis가 판별할 수 있다. 호출자는 보통 JVM
     * 기동 시 한 번 발급한 고정 token을 계속 재사용한다.
     */
    public boolean tryAcquireOrRenew(String key, Duration ttl, String token) {
        Long result = redisTemplate.execute(
            ACQUIRE_OR_RENEW_SCRIPT, List.of(key), token, String.valueOf(ttl.toMillis()));
        return result != null && result == 1L;
    }
}
