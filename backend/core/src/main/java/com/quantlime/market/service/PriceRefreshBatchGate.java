package com.quantlime.market.service;

import com.quantlime.score.domain.PeerGroup;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 전종목 가격·스코어 fan-out(2026-09-24)의 fan-in 배리어. {@code
 * MarketDataRefreshService.refreshAll()}이 종목별 Kafka 이벤트를 발행한 뒤,
 * 그 종목들을 처리하는 컨슈머(다른 스레드·다른 모듈)가 전부 끝났는지를 Redis
 * 카운터로 판단한다 - JVM 로컬 카운터로는 안 되는 이유는 fan-out의 소비자가
 * 이 프로세스와 다른 스레드 풀(Kafka 리스너 컨테이너)에서 동작하기 때문.
 *
 * <p>{@link #completeOne}은 {@code GeminiDailyQuotaGate}와 동일하게 원자적
 * INCR/DECR을 쓴다 - 여러 컨슈머 스레드가 동시에 호출해도 정확히 한 번만
 * 0에 도달한 것으로 판정된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceRefreshBatchGate {

    private static final String KEY_PREFIX = "market:refresh:";
    // 락(MarketDataRefreshService.LOCK_TTL=60분)보다 짧게 잡아, 배치가
    // 영원히 안 끝나는 최악의 경우에도 이 카운터가 락보다 먼저 청소되게 한다.
    private static final Duration KEY_TTL = Duration.ofHours(2);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private final StringRedisTemplate redisTemplate;

    /** 배치 시작 시 이번에 발행할 종목 수를 등록한다. 0건이면 키 자체를 만들지 않는다 - {@link #awaitCompletion}이 "키 없음"을 즉시완료로 해석한다. */
    public void startBatch(String runId, PeerGroup peerGroup, int total) {
        if (total <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(key(runId, peerGroup), String.valueOf(total), KEY_TTL);
    }

    /**
     * 종목 하나의 처리가 (성공이든, 재시도 소진 후 DLT든) 최종적으로 끝났음을
     * 알린다. 재시도 중간 단계에서는 호출하면 안 된다 - 한 종목이 재시도마다
     * 카운터를 깎으면 실제보다 먼저 0에 도달해 배치가 끝나지 않았는데도
     * 정규화가 도는 문제가 생긴다.
     */
    public void completeOne(String runId, PeerGroup peerGroup) {
        redisTemplate.opsForValue().decrement(key(runId, peerGroup));
    }

    /** 배치가 끝날 때까지 폴링 대기한다. 시간 내에 못 끝나면 false. */
    public boolean awaitCompletion(String runId, PeerGroup peerGroup, Duration timeout) {
        String key = key(runId, peerGroup);
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            String remaining = redisTemplate.opsForValue().get(key);
            if (remaining == null || "0".equals(remaining)) {
                return true;
            }
            if (!sleepPollInterval()) {
                return false;
            }
        }
        log.warn("가격 갱신 배치 대기 시간 초과: runId={}, peerGroup={}, remaining={}",
            runId, peerGroup, redisTemplate.opsForValue().get(key));
        return false;
    }

    private boolean sleepPollInterval() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String key(String runId, PeerGroup peerGroup) {
        return KEY_PREFIX + runId + ":" + peerGroup.getWireValue() + ":remaining";
    }
}
