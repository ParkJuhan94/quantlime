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
 * 그 종목들을 처리하는 컨슈머(다른 스레드·다른 모듈)가 전부 끝났는지를 Redis로
 * 판단한다 - JVM 로컬 카운터로는 안 되는 이유는 fan-out의 소비자가 이
 * 프로세스와 다른 스레드 풀(Kafka 리스너 컨테이너)에서 동작하기 때문.
 *
 * <p><b>완료 집합(SET) 방식인 이유</b>(2026-10-01, Kafka 점검): 처음엔 남은 개수를
 * DECR로 줄이는 카운터였는데, Kafka는 at-least-once라 리밸런스/재시작 시 이미
 * 처리한 종목이 재전달되면 같은 종목이 두 번 감소해 카운터가 일찍 0이 되고, 그러면
 * 일부 종목이 빠진 채 횡단면 정규화가 돈다. 종목코드를 SET에 넣는 방식은 재전달이
 * 몇 번이든 한 종목은 한 번만 세어진다. 총 개수는 별도 키에 둔다.
 *
 * <p><b>만료/종료된 배치는 무시한다</b>: 총 개수 키(TTL)가 없으면 그 배치는 끝났거나
 * 만료된 것이다. 오래된 runId의 메시지(중단된 배치의 잔여분 등)가 뒤늦게 소비돼도
 * {@link #isActive}로 걸러 외부 API 호출을 낭비하지 않고, {@link #completeOne}이
 * TTL 없는 키를 새로 만들지 않는다(DECR/SADD는 없는 키를 만들어 영구히 남긴다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceRefreshBatchGate {

    private static final String KEY_PREFIX = "market:refresh:";
    // 락(MarketDataRefreshService.LOCK_TTL=120분)보다 길게 잡아, 락이 풀린 뒤에야 청소되게 한다.
    private static final Duration KEY_TTL = Duration.ofHours(3);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private final StringRedisTemplate redisTemplate;

    /** 배치 시작 시 이번에 발행할 종목 수를 등록한다. 0건이면 키 자체를 만들지 않는다 - {@link #awaitCompletion}이 "키 없음"을 즉시완료로 해석한다. */
    public void startBatch(String runId, PeerGroup peerGroup, int total) {
        if (total <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(totalKey(runId, peerGroup), String.valueOf(total), KEY_TTL);
    }

    /** 이 배치가 아직 진행 중(등록됐고 TTL이 안 지남)인지. false면 컨슈머는 그 메시지를 처리하지 않고 넘긴다. */
    public boolean isActive(String runId, PeerGroup peerGroup) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(totalKey(runId, peerGroup)));
    }

    /**
     * 종목 하나의 처리가 (성공이든, 재시도 소진 후 DLT든) 최종적으로 끝났음을
     * 알린다. 재시도 중간 단계에서는 호출하면 안 된다 - 한 종목이 재시도마다
     * 호출되면 실제보다 먼저 완료로 판정돼 배치가 끝나지 않았는데도 정규화가 돈다.
     * 같은 종목을 여러 번 호출해도 한 번만 센다(재전달 멱등).
     */
    public void completeOne(String runId, PeerGroup peerGroup, String stockCode) {
        if (!isActive(runId, peerGroup)) {
            log.debug("만료/종료된 배치의 완료 통지, 무시: runId={}, peerGroup={}, stockCode={}",
                runId, peerGroup, stockCode);
            return;
        }
        String doneKey = doneKey(runId, peerGroup);
        redisTemplate.opsForSet().add(doneKey, stockCode);
        // SADD가 키를 새로 만들 수 있으므로 매번 TTL을 건다 - TTL 없는 키가 남지 않게 한다.
        redisTemplate.expire(doneKey, KEY_TTL);
    }

    /** 배치가 끝날 때까지 폴링 대기한다. 시간 내에 못 끝나면 false. */
    public boolean awaitCompletion(String runId, PeerGroup peerGroup, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            if (isComplete(runId, peerGroup)) {
                return true;
            }
            if (!Instant.now().isBefore(deadline) || !sleepPollInterval()) {
                break;
            }
        }
        log.warn("가격 갱신 배치 대기 시간 초과: runId={}, peerGroup={}, 진행={}", runId, peerGroup,
            progress(runId, peerGroup));
        return false;
    }

    private boolean isComplete(String runId, PeerGroup peerGroup) {
        String total = redisTemplate.opsForValue().get(totalKey(runId, peerGroup));
        if (total == null) {
            return true;
        }
        Long done = redisTemplate.opsForSet().size(doneKey(runId, peerGroup));
        return done != null && done >= Long.parseLong(total);
    }

    private String progress(String runId, PeerGroup peerGroup) {
        String total = redisTemplate.opsForValue().get(totalKey(runId, peerGroup));
        Long done = redisTemplate.opsForSet().size(doneKey(runId, peerGroup));
        return (done == null ? 0 : done) + "/" + (total == null ? "?" : total);
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

    private String totalKey(String runId, PeerGroup peerGroup) {
        return KEY_PREFIX + runId + ":" + peerGroup.getWireValue() + ":total";
    }

    private String doneKey(String runId, PeerGroup peerGroup) {
        return KEY_PREFIX + runId + ":" + peerGroup.getWireValue() + ":done";
    }
}
