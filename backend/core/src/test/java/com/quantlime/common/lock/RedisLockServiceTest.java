package com.quantlime.common.lock;

import com.quantlime.support.DataJpaTestSupport;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest 슬라이스는 기본적으로 Redis 관련 자동설정을 포함하지 않아
// StringRedisTemplate 빈이 없다 - RedisAutoConfiguration을 함께 @Import해
// TestContainerSupport가 띄운 실제 Redis 컨테이너에 연결되게 한다
// (ChannelVelocityInitializationServiceTest와 동일하게, 목(mock)이 아닌
// 실제 빈으로 검증해야 하는 이유: 락 해제가 Lua CAS 스크립트로 Redis
// 서버에서 직접 평가되므로 Mockito로는 그 동작 자체를 재현할 수 없다).
@Tag("integration")
@Import({RedisAutoConfiguration.class, RedisLockService.class})
class RedisLockServiceTest extends DataJpaTestSupport {

    @Autowired
    private RedisLockService redisLockService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("[락이 이미 잡혀있으면 task를 실행하지 않고 빈 Optional을 반환한다]")
    void runExclusively_returnsEmpty_whenAlreadyLocked() {
        // given
        String key = "lock:test:" + System.nanoTime();
        redisTemplate.opsForValue().setIfAbsent(key, "other-token", Duration.ofSeconds(30));
        AtomicInteger callCount = new AtomicInteger(0);

        // when
        Optional<String> result = redisLockService.runExclusively(
            key, Duration.ofSeconds(30), () -> {
                callCount.incrementAndGet();
                return "done";
            });

        // then
        assertThat(result).isEmpty();
        assertThat(callCount.get()).isZero();
    }

    @Test
    @DisplayName("[task 실행이 끝나면 락이 해제돼 같은 key를 다시 획득할 수 있다]")
    void runExclusively_releasesLock_afterTaskCompletes() {
        // given
        String key = "lock:test:" + System.nanoTime();

        // when
        Optional<String> first = redisLockService.runExclusively(
            key, Duration.ofSeconds(30), () -> "first");
        Optional<String> second = redisLockService.runExclusively(
            key, Duration.ofSeconds(30), () -> "second");

        // then
        assertThat(first).contains("first");
        assertThat(second).contains("second");
    }

    @Test
    @DisplayName("[task가 예외를 던져도 락은 해제된다]")
    void runExclusively_releasesLock_evenWhenTaskThrows() {
        // given
        String key = "lock:test:" + System.nanoTime();

        // when
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
            redisLockService.runExclusively(key, Duration.ofSeconds(30), () -> {
                throw new RuntimeException("작업 실패");
            }));
        Optional<String> retryAfterFailure = redisLockService.runExclusively(
            key, Duration.ofSeconds(30), () -> "recovered");

        // then
        assertThat(retryAfterFailure).contains("recovered");
    }

    @Test
    @DisplayName("[TTL 만료 후 다른 실행이 락을 새로 잡으면, 원래 소유자가 뒤늦게 반환돼도 "
        + "그 락을 지우지 않는다(토큰 펜싱)]")
    void runExclusively_doesNotReleaseAnotherOwnersLock_afterTtlExpires() throws Exception {
        // given: A가 짧은 TTL로 락을 잡고, TTL보다 오래 걸리는 작업을 수행한다
        String key = "lock:test:" + System.nanoTime();
        Duration shortTtl = Duration.ofMillis(300);

        CompletableFuture<Optional<String>> ownerA = CompletableFuture.supplyAsync(() ->
            redisLockService.runExclusively(key, shortTtl, () -> {
                sleepQuietly(Duration.ofMillis(700));
                return "a-done";
            }));

        // when: A의 TTL이 지나 락이 자연 만료된 뒤, B가 같은 key로 새 락을 잡는다
        sleepQuietly(Duration.ofMillis(500));
        Boolean bAcquired = redisTemplate.opsForValue()
            .setIfAbsent(key, "b-token", Duration.ofSeconds(30));
        Optional<String> aResult = ownerA.get();

        // then: A는 정상적으로 자기 task를 완료해 반환하지만(자기가 락을 쥔 줄 알고 있으므로),
        // 뒤늦은 해제 시도가 B의 락(b-token)을 지우지는 못한다
        assertThat(bAcquired).isTrue();
        assertThat(aResult).contains("a-done");
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("b-token");
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
