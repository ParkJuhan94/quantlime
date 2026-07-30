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
 */
@Component
@RequiredArgsConstructor
public class RedisLockService {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]) "
            + "else return 0 end",
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
}
