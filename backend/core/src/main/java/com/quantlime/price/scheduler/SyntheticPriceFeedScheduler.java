package com.quantlime.price.scheduler;

import com.quantlime.common.lock.PriceRelayLeaderGate;
import com.quantlime.common.util.SafeExecutor;
import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.cache.WatchlistedStockCodeCache;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.util.ChangeRateCalculator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 로컬 실시간 시세 fan-out 수평확장 검증(docker-compose.scaleout.yml)
 * 전용 시세 소스 - {@code price-feed.mode=synthetic}일 때만 활성화된다
 * (기본값은 {@code toss}라 운영/평소 로컬 개발에는 전혀 관여하지 않는다).
 * 실제 Toss 호출(DomesticMarketPriceSweepScheduler/
 * OverseasWatchlistPriceScheduler, 이 모드에서는 둘 다 비활성)을 대체해
 * 관심종목 풀에 랜덤워크 시세를 만들어 넣는다 - 목적은 두 가지: (1) Toss
 * 쿼터를 전혀 안 써 운영과 같은 계정을 공유해도 429/차단 위험이 없고,
 * (2) {@code DomesticMarketCalendarCache}/{@code OverseasMarketCalendarCache}의
 * 장중 게이트도 이 모드에서는 항상 통과하도록 우회돼 있어(각 클래스 참고)
 * 장 마감 시간대에도 언제든 fan-out을 재현·검증할 수 있다.
 *
 * <p><b>국내와 해외를 비대칭으로 처리한다.</b> 국내는 스냅샷을
 * {@link PriceCacheStore}에 저장하기만 하면 된다 - 브로드캐스트는
 * {@code DomesticWatchlistPriceRelayScheduler}가 모드 무관하게 항상 돌며
 * 그 스냅샷을 읽어 대신 처리한다. 해외는 원래 스윕(fetch)과 릴레이
 * (broadcast)가 {@code OverseasWatchlistPriceScheduler} 한 클래스에
 * 묶여 있어 그 클래스 자체가 이 모드에서 통째로 꺼지므로, 이 스케줄러가
 * 저장뿐 아니라 브로드캐스트까지 대신 수행한다(그 클래스의
 * {@code fetchAndBroadcastChunk}와 동일한 목적지 규칙 재사용).
 *
 * <p>국내 "관심종목만 보기" 랭킹({@code DomesticMarketRankingCache})은 이
 * 스케줄러가 채우지 않는다 - 그 캐시는 원래 전종목 스윕
 * (지금은 비활성)의 부산물이고, 이 검증의 목적(WS fan-out 정합성/중복
 * 배수)은 랭킹 API를 전혀 쓰지 않아 범위 밖으로 남겨뒀다. 해외 랭킹은
 * {@code MarketRankingService.overseasWatchlistRanking}이 매 요청마다
 * {@link PriceCacheStore}를 직접 읽으므로 이 스케줄러가 저장만 해도
 * 자동으로 맞는다.
 *
 * <p>인스턴스별 랜덤워크 상태({@link #lastPriceByCode})는 JVM 로컬이다 -
 * {@link PriceRelayLeaderGate}로 항상 리더 하나만 이 스케줄러를 돌게
 * 되므로(feat/realtime-fanout-scaleout, 2026-09-25) 실제로는 이 상태가
 * 여러 인스턴스에 흩어질 일이 없지만, 설령 리더 전환이 일어나도(이전
 * 리더 크래시 등) 새 리더가 그 시점의 {@link PreviousCloseCache} 기준으로
 * 다시 시드하고 이어서 랜덤워크할 뿐이라 별도 마이그레이션이 필요 없다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "price-feed.mode", havingValue = "synthetic")
@RequiredArgsConstructor
public class SyntheticPriceFeedScheduler {

    // 점(.) 구분자 채택 이유는 DomesticWatchlistPriceRelayScheduler의 동일
    // 상수 주석 참고.
    private static final String PRICE_TOPIC_PREFIX = "/topic/price.";
    // 틱당 최대 ±0.5% 랜덤워크 - 눈으로 보기에 충분히 움직이면서도
    // 음수/0으로 튈 걱정이 없는 보수적인 폭.
    private static final double MAX_STEP_RATIO = 0.005;
    private static final double DOMESTIC_BASE_PRICE = 50_000.0;
    private static final double OVERSEAS_BASE_PRICE = 100.0;

    // 필드명이 PriceCacheConfig의 @Bean 메서드명과 일치해야 Spring이 같은
    // 타입(WatchlistedStockCodeCache/PreviousCloseCache)의 두 Bean 중 이걸
    // 고른다(By-Name 디스앰비규에이션 - 다른 시세 스케줄러들과 동일 관례).
    private final WatchlistedStockCodeCache domesticWatchlistedStockCodeCache;
    private final WatchlistedStockCodeCache overseasWatchlistedStockCodeCache;
    private final PreviousCloseCache domesticPreviousCloseCache;
    private final PreviousCloseCache overseasPreviousCloseCache;
    private final PriceCacheStore priceCacheStore;
    private final SimpMessagingTemplate messagingTemplate;
    private final PriceRelayLeaderGate priceRelayLeaderGate;

    private final Map<String, Double> lastPriceByCode = new ConcurrentHashMap<>();

    // 실제 Toss 스윕/릴레이와 동일한 주기(REALTIME_PRICE_POLL_INTERVAL_MS)
    // + 전용 풀(SchedulerConfig.priceSweepTaskScheduler) 재사용.
    //
    // 리더 인스턴스에서만 돈다(2026-09-25, PriceRelayLeaderGate 참고) -
    // 이 스케줄러도 실제 Toss 스윕/릴레이와 동일한 "인스턴스마다 각자
    // 돌면 안 되는" 생산자이므로 같은 게이트를 공유한다(클래스 javadoc의
    // "인스턴스별 랜덤워크 상태" 비결정성도 이걸로 사라진다).
    @Scheduled(fixedDelayString = "${realtime-price.poll-interval-ms:3000}",
        scheduler = "priceSweepTaskScheduler")
    public void feedOnce() {
        if (!priceRelayLeaderGate.isLeader()) {
            return;
        }
        SafeExecutor.runSafely("로컬 scale-out 합성 시세 피딩",
            () -> {
                feedDomestic();
                feedOverseas();
            });
    }

    private void feedDomestic() {
        List<String> codes = domesticWatchlistedStockCodeCache.get();
        if (codes.isEmpty()) {
            return;
        }
        Map<String, Double> previousCloseByCode = domesticPreviousCloseCache.get(codes);
        List<PriceSnapshot> snapshots = codes.stream()
            .map(code -> nextSnapshot(code, DOMESTIC_BASE_PRICE, previousCloseByCode))
            .toList();
        priceCacheStore.saveAll(snapshots);
    }

    // 해외는 OverseasWatchlistPriceScheduler가 synthetic 모드에서 통째로
    // 꺼지므로(클래스 javadoc 참고) 저장뿐 아니라 브로드캐스트까지 그
    // 클래스의 fetchAndBroadcastChunk와 동일하게 여기서 수행한다.
    private void feedOverseas() {
        List<String> codes = overseasWatchlistedStockCodeCache.get();
        if (codes.isEmpty()) {
            return;
        }
        Map<String, Double> previousCloseByCode = overseasPreviousCloseCache.get(codes);
        List<PriceSnapshot> snapshots = new ArrayList<>();
        for (String code : codes) {
            snapshots.add(nextSnapshot(code, OVERSEAS_BASE_PRICE, previousCloseByCode));
        }
        priceCacheStore.saveAll(snapshots);
        for (PriceSnapshot snapshot : snapshots) {
            messagingTemplate.convertAndSend(PRICE_TOPIC_PREFIX + snapshot.stockCode(), snapshot);
        }
    }

    private PriceSnapshot nextSnapshot(String code, double basePrice, Map<String, Double> previousCloseByCode) {
        double previous = lastPriceByCode.computeIfAbsent(code,
            c -> previousCloseByCode.getOrDefault(c, basePrice));
        double stepRatio = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * MAX_STEP_RATIO;
        double next = previous * (1 + stepRatio);
        lastPriceByCode.put(code, next);

        Double previousClose = previousCloseByCode.get(code);
        Double changeRate = ChangeRateCalculator.calculate(next, previousClose);
        return new PriceSnapshot(code, next, changeRate, Instant.now().toString());
    }
}
