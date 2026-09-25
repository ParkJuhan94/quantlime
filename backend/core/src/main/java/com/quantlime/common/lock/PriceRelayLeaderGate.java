package com.quantlime.common.lock;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * backend가 2대 이상인 환경에서, 고빈도 시세 생산 스케줄러(전종목 스윕
 * + 국내/해외 릴레이 + 로컬 검증용 합성 피더)가 인스턴스 중 정확히
 * 하나에서만 돌게 막는다. {@link RedisLockService#tryAcquireOrRenew}로
 * 매 틱 리더 지위를 "획득 또는 연장"하는 방식이라 - 기존
 * {@link RedisLockService#runExclusively}(잡고 즉시 실행 후 바로 해제)를
 * 그대로 썼다면 틱 하나가 수 ms~3초 안에 끝나 다음 틱엔 다른 인스턴스가
 * 잡을 수 있고, 그러면 "인스턴스 수만큼 중복 발행"이 그대로 재현됐을
 * 것이다(docs/00-sre/SRE.md §5-2 #4, feat/realtime-fanout-scaleout
 * 2026-09-25).
 *
 * <p>모든 시세 생산 스케줄러가 같은 락 키({@value #LEADER_KEY}) 하나를
 * 공유한다 - 스윕/릴레이/합성피더가 서로 다른 리더가 되면 스윕 리더가
 * 아닌 인스턴스에서 릴레이가 도는 경우 그 인스턴스는 자기가 만든 게
 * 아닌 Redis 스냅샷을 읽어 브로드캐스트하게 되는데, 그 자체는 무해하지만
 * (PriceCacheStore가 이미 공유 상태) 리더 전환 시점을 하나로 묶어 관측·
 * 디버깅을 단순하게 유지하기 위해 의도적으로 단일 키로 통일했다.
 *
 * <p>TTL 10초는 가장 짧은 틱(전종목 스윕, 100ms)의 100배, 나머지 틱
 * (3초)의 3배 이상 - 매 틱 연장되는 값이라 정상 동작 중에는 절대 만료되지
 * 않고, 리더가 실제로 죽었을 때만(재기동/크래시) 다음 틱들이 몇 초간
 * 공백(어느 인스턴스도 발행하지 않음) 후 새 리더가 이어받는다. 이 공백은
 * PriceCacheStore(TTL 5분)/RabbitMQ 구독 상태 어느 쪽도 깨뜨리지 않는
 * "일시 정지"일 뿐이라 안전하게 감수한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceRelayLeaderGate {

    private static final String LEADER_KEY = "lock:price-relay-leader";
    private static final Duration LEASE_TTL = Duration.ofSeconds(10);

    private final RedisLockService redisLockService;

    // JVM(인스턴스)당 한 번만 발급 - 매 틱 이 값을 그대로 재사용해야
    // "지난 틱에 내가 잡았던 리더 지위를 이번 틱에도 내가 이어받는다"를
    // Redis가 tryAcquireOrRenew의 CAS로 판별할 수 있다.
    private final String instanceToken = UUID.randomUUID().toString();

    private volatile boolean wasLeader = false;

    /**
     * 이번 틱에 이 인스턴스가 리더인지 확인(필요하면 리더 지위를 새로
     * 획득)한다. 호출할 때마다 TTL을 연장하므로, 리더인 스케줄러는 매
     * 틱 이 메서드를 먼저 호출해야 한다 - 호출을 건너뛰면(예: 게이트
     * 통과 후 작업만 하고 다음 틱에 다시 확인 안 함) TTL이 흘러 다른
     * 인스턴스에 리더를 뺏길 수 있다.
     */
    public boolean isLeader() {
        boolean leaderNow = redisLockService.tryAcquireOrRenew(LEADER_KEY, LEASE_TTL, instanceToken);
        if (leaderNow != wasLeader) {
            log.info("시세 릴레이 리더 지위 전환: leader={}, instanceToken={}", leaderNow, instanceToken);
            wasLeader = leaderNow;
        }
        return leaderNow;
    }
}
