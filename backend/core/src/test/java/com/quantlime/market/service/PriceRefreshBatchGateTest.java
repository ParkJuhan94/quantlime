package com.quantlime.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.score.domain.PeerGroup;
import com.quantlime.support.DataJpaTestSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * fan-in 배리어의 핵심 성질(재전달 멱등, 만료된 배치 무시, TTL 없는 키 미생성)은 Redis 서버
 * 의미(SADD/EXPIRE/없는 키 처리)에 달려 있어 Mockito로는 재현할 수 없다 - {@code
 * PaymentWebhookDedupStoreTest}와 동일하게 TestContainerSupport의 실제 Redis로 검증한다.
 */
@Tag("integration")
@Import({RedisAutoConfiguration.class, PriceRefreshBatchGate.class})
class PriceRefreshBatchGateTest extends DataJpaTestSupport {

    // 첫 확인에서 끝나지 않으면 대기 없이 곧바로 false를 반환하게 하는 값(폴링 간격 2초를 기다리지 않는다).
    private static final Duration NO_WAIT = Duration.ZERO;

    @Autowired
    private PriceRefreshBatchGate gate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static String newRunId() {
        return "run-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("[발행한 종목이 전부 완료되면 배치가 끝난 것으로 판정한다]")
    void awaitCompletion_allStocksCompleted_returnsTrue() {
        // given
        String runId = newRunId();
        gate.startBatch(runId, PeerGroup.DOMESTIC, 2);

        // when
        gate.completeOne(runId, PeerGroup.DOMESTIC, "005930");
        gate.completeOne(runId, PeerGroup.DOMESTIC, "000660");

        // then
        assertThat(gate.awaitCompletion(runId, PeerGroup.DOMESTIC, NO_WAIT)).isTrue();
    }

    @Test
    @DisplayName("[같은 종목의 완료 통지가 재전달돼 여러 번 와도 한 번만 센다 - 조기 완료 판정 방지]")
    void completeOne_redeliveredSameStock_countsOnce() {
        // given
        String runId = newRunId();
        gate.startBatch(runId, PeerGroup.DOMESTIC, 2);

        // when - 리밸런스/재시작으로 같은 종목 메시지가 두 번 소비된 상황
        gate.completeOne(runId, PeerGroup.DOMESTIC, "005930");
        gate.completeOne(runId, PeerGroup.DOMESTIC, "005930");

        // then - 2건 중 1종목만 끝났으므로 아직 완료가 아니다(DECR 카운터였다면 여기서 0이 됐다)
        assertThat(gate.awaitCompletion(runId, PeerGroup.DOMESTIC, NO_WAIT)).isFalse();

        gate.completeOne(runId, PeerGroup.DOMESTIC, "000660");
        assertThat(gate.awaitCompletion(runId, PeerGroup.DOMESTIC, NO_WAIT)).isTrue();
    }

    @Test
    @DisplayName("[만료/종료된 배치의 완료 통지는 무시하고 TTL 없는 키를 새로 만들지 않는다]")
    void completeOne_inactiveRun_isNoOpAndCreatesNoKeys() {
        // given - startBatch를 하지 않은(또는 TTL로 사라진) 오래된 runId
        String staleRunId = newRunId();

        // when
        gate.completeOne(staleRunId, PeerGroup.DOMESTIC, "005930");

        // then
        assertThat(gate.isActive(staleRunId, PeerGroup.DOMESTIC)).isFalse();
        assertThat(redisTemplate.keys("market:refresh:" + staleRunId + "*")).isEmpty();
    }

    @Test
    @DisplayName("[isActive는 startBatch 이후에만 true이고, 국내/해외 배치는 서로 독립적이다]")
    void isActive_onlyAfterStartBatch_andPeerGroupsAreIndependent() {
        // given
        String runId = newRunId();
        assertThat(gate.isActive(runId, PeerGroup.DOMESTIC)).isFalse();

        // when
        gate.startBatch(runId, PeerGroup.DOMESTIC, 1);

        // then
        assertThat(gate.isActive(runId, PeerGroup.DOMESTIC)).isTrue();
        assertThat(gate.isActive(runId, PeerGroup.OVERSEAS)).isFalse();

        gate.completeOne(runId, PeerGroup.DOMESTIC, "005930");
        assertThat(gate.awaitCompletion(runId, PeerGroup.DOMESTIC, NO_WAIT)).isTrue();
        // 해외는 시작하지 않았으므로 "키 없음 = 즉시 완료"로 해석된다
        assertThat(gate.awaitCompletion(runId, PeerGroup.OVERSEAS, NO_WAIT)).isTrue();
    }

    @Test
    @DisplayName("[0건 배치는 키를 만들지 않고 즉시 완료로 본다]")
    void startBatch_zeroTotal_createsNoKeysAndCompletesImmediately() {
        // given
        String runId = newRunId();

        // when
        gate.startBatch(runId, PeerGroup.OVERSEAS, 0);

        // then
        assertThat(redisTemplate.keys("market:refresh:" + runId + "*")).isEmpty();
        assertThat(gate.awaitCompletion(runId, PeerGroup.OVERSEAS, NO_WAIT)).isTrue();
    }

    @Test
    @DisplayName("[총 개수 키와 완료 집합 키 둘 다 TTL이 걸려 있다 - 영구히 남는 키가 없다]")
    void keys_haveTtl() {
        // given
        String runId = newRunId();
        gate.startBatch(runId, PeerGroup.DOMESTIC, 2);

        // when
        gate.completeOne(runId, PeerGroup.DOMESTIC, "005930");

        // then - TTL이 없으면 getExpire가 -1을 반환한다
        Long totalTtl = redisTemplate.getExpire("market:refresh:" + runId + ":domestic:total");
        Long doneTtl = redisTemplate.getExpire("market:refresh:" + runId + ":domestic:done");
        assertThat(totalTtl).isPositive();
        assertThat(doneTtl).isPositive();
    }
}
