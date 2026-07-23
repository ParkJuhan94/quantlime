package com.quantlime.common.lock;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis SETNX 기반 분산락. 여러 인스턴스가 같은 스케줄 작업을 동시에
 * 실행하는 것을 막는 용도(현재는 단일 EC2 배포지만 스케일아웃 시에도
 * 그대로 유효) - Redisson 없이 이미 의존 중인 spring-data-redis만으로
 * 충분해 새 라이브러리를 추가하지 않았다.
 */
@Component
@RequiredArgsConstructor
public class RedisLockService {

    private static final String LOCKED_VALUE = "locked";

    private final StringRedisTemplate redisTemplate;

    public boolean tryLock(String key, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, LOCKED_VALUE, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(String key) {
        redisTemplate.delete(key);
    }
}
