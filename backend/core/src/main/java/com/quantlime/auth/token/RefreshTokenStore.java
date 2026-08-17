package com.quantlime.auth.token;

import com.quantlime.auth.jwt.JwtProperties;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 장애 시 예외를 흡수하는 폴백을 일부러 두지 않는다(PriceCacheStore
 * 등 시세 캐시와 다른 판단) - 리프레시 토큰은 인증 상태 그 자체라, 저장/조회
 * 실패를 조용히 넘기면 위조되지 않은 토큰까지 로그인 실패로 잘못 처리하거나
 * (find 실패를 "토큰 없음"으로 오인) 반대로 무효화가 안 먹히는 것처럼 보일
 * 수 있다. Redis가 죽으면 로그인/재발급이 그대로 실패(fail-closed)하는 게
 * 맞는 동작이다(2026-08-17, docs/RELIABILITY.md 참고).
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
            key(userId),
            refreshToken,
            Duration.ofMillis(jwtProperties.getRefreshTokenValidity())
        );
    }

    public Optional<String> findByUserId(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return REFRESH_TOKEN_KEY_PREFIX + userId;
    }
}
