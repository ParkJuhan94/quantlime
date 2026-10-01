package com.quantlime.payment.implement;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.support.DataJpaTestSupport;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

// SETNX 원자성과 TTL은 Redis 서버가 보장하는 동작이라 Mockito로는 재현할 수 없다 -
// RedisLockServiceTest와 동일하게 TestContainerSupport의 실제 Redis 컨테이너로 검증한다
@Tag("integration")
@Import({RedisAutoConfiguration.class, PaymentWebhookDedupStore.class})
class PaymentWebhookDedupStoreTest extends DataJpaTestSupport {

    @Autowired
    private PaymentWebhookDedupStore dedupStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("[처음 보는 payloadHash는 true(처리 진행), 같은 해시의 두 번째 호출은 false(중복 스킵)]")
    void markProcessedIfAbsent_firstTrue_secondFalse() {
        // given
        String hash = "hash-" + System.nanoTime();

        // when & then
        assertThat(dedupStore.markProcessedIfAbsent(hash)).isTrue();
        assertThat(dedupStore.markProcessedIfAbsent(hash)).isFalse();
    }

    @Test
    @DisplayName("[서로 다른 payloadHash는 독립적으로 처리된다]")
    void markProcessedIfAbsent_differentHashes_areIndependent() {
        // given
        long suffix = System.nanoTime();

        // when & then
        assertThat(dedupStore.markProcessedIfAbsent("a-" + suffix)).isTrue();
        assertThat(dedupStore.markProcessedIfAbsent("b-" + suffix)).isTrue();
    }

    @Test
    @DisplayName("[마킹 키에 약 24시간 TTL이 설정돼 영구히 남지 않는다]")
    void markProcessedIfAbsent_setsTtl() {
        // given
        String hash = "ttl-" + System.nanoTime();

        // when
        dedupStore.markProcessedIfAbsent(hash);

        // then
        Long ttlSeconds = redisTemplate.getExpire("payment:webhook:dedup:" + hash);
        assertThat(ttlSeconds).isNotNull()
            .isBetween(Duration.ofHours(23).toSeconds(), Duration.ofHours(24).toSeconds());
    }
}
