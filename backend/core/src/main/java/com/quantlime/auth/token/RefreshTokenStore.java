package com.quantlime.auth.token;

import com.quantlime.auth.jwt.JwtProperties;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

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
