package com.quantlime.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.auth.jwt.JwtProperties;
import com.quantlime.support.DataJpaTestSupport;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

// 키 접두사·TTL은 실제 Redis 값으로 확인해야 의미가 있어 RedisLockServiceTest와
// 같은 방식(Testcontainers Redis)으로 검증한다
@Tag("integration")
@Import(RedisAutoConfiguration.class)
class RefreshTokenStoreTest extends DataJpaTestSupport {

    private static final long REFRESH_VALIDITY_MS = 3_600_000L;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RefreshTokenStore store;
    private Long userId;

    @BeforeEach
    void setUp() {
        store = new RefreshTokenStore(redisTemplate,
            new JwtProperties("test-jwt-secret-key-for-unit-test-must-be-long-enough-1234567890",
                60_000L, REFRESH_VALIDITY_MS));
        userId = System.nanoTime();
    }

    @Test
    @DisplayName("[저장한 리프레시 토큰을 userId로 조회할 수 있다]")
    void save_thenFind() {
        store.save(userId, "refresh-1");

        assertThat(store.findByUserId(userId)).contains("refresh-1");
    }

    @Test
    @DisplayName("[같은 사용자가 다시 저장하면 이전 토큰을 덮어쓴다(재발급 시 로테이션)]")
    void save_overwritesPrevious() {
        store.save(userId, "old");
        store.save(userId, "new");

        assertThat(store.findByUserId(userId)).contains("new");
    }

    @Test
    @DisplayName("[저장된 적 없는 사용자는 빈 Optional]")
    void find_unknownUser_returnsEmpty() {
        assertThat(store.findByUserId(userId)).isEmpty();
    }

    @Test
    @DisplayName("[delete 후에는 조회되지 않는다(로그아웃 무효화)]")
    void delete_invalidatesToken() {
        store.save(userId, "refresh-1");

        store.delete(userId);

        assertThat(store.findByUserId(userId)).isEmpty();
    }

    @Test
    @DisplayName("[토큰 TTL은 설정된 리프레시 유효기간을 따른다]")
    void save_setsTtlFromProperties() {
        store.save(userId, "refresh-1");

        Long ttlSeconds = redisTemplate.getExpire("auth:refresh:" + userId);
        assertThat(ttlSeconds).isNotNull()
            .isBetween(Duration.ofMillis(REFRESH_VALIDITY_MS).toSeconds() - 5,
                Duration.ofMillis(REFRESH_VALIDITY_MS).toSeconds());
    }
}
