package com.quantlime.price.scheduler;

import com.quantlime.common.lock.PriceRelayLeaderGate;
import com.quantlime.common.util.SafeExecutor;
import com.quantlime.price.cache.DomesticMarketCalendarCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.cache.WatchlistedStockCodeCache;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.realtime.PriceTopicSubscriptionTracker;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 관심 종목의 최신 시세를 STOMP로 브로드캐스트한다. Toss를 직접 호출하지
 * 않고 {@code DomesticMarketPriceSweepScheduler}가 전종목 스윕마다 {@link PriceCacheStore}
 * (Redis)에 적재해둔 스냅샷만 읽어 중계("relay")만 한다 - 두 스케줄러가
 * 각각 Toss를 호출해 관심종목 가격을 중복 조회하던 구조를 단일
 * 파이프라인으로 통합했다({@code DomesticMarketPriceSweepScheduler} 참고,
 * 2026-07-15). 이름도 "직접 조회해 브로드캐스트"가 아니라 "캐시를
 * 중계만 한다"는 실제 역할에 맞춰 {@code PriceBroadcastScheduler}에서
 * 변경함(2026-07-16).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomesticWatchlistPriceRelayScheduler {

    // RabbitMQ STOMP relay는 /topic/<name>의 name에 슬래시가 있으면 "not a
    // valid topic destination"으로 거부한다(SimpleBroker는 문제없었음) -
    // relay 도입에 맞춰 점(.) 구분자로 변경(2026-09-25, WebSocketConfig
    // 참고). 프론트(stompClient.ts)/load-test(ws-stocks.js)도 함께 맞춤.
    private static final String PRICE_TOPIC_PREFIX = "/topic/price.";

    private final DomesticMarketCalendarCache domesticMarketCalendarCache;
    // 필드명이 PriceCacheConfig의 @Bean 메서드명(domesticWatchlistedStockCodeCache)과
    // 일치해야 Spring이 같은 타입(WatchlistedStockCodeCache)의 두 Bean 중
    // 이걸 고른다(By-Name 디스앰비규에이션).
    private final WatchlistedStockCodeCache domesticWatchlistedStockCodeCache;
    private final PriceCacheStore priceCacheStore;
    private final SimpMessagingTemplate messagingTemplate;
    private final PriceRelayLeaderGate priceRelayLeaderGate;
    private final PriceTopicSubscriptionTracker priceTopicSubscriptionTracker;

    // 전용 풀(SchedulerConfig.priceSweepTaskScheduler)에서 실행 - 사유는
    // DomesticMarketPriceSweepScheduler 참고(2026-08-17).
    //
    // 리더 인스턴스에서만 돈다(2026-09-25, PriceRelayLeaderGate 참고) -
    // 그 전엔 인스턴스마다 각자 이 틱이 돌아, 관심종목 하나당 인스턴스
    // 수만큼 convertAndSend가 중복 발행됐다(RabbitMQ relay처럼 발행이
    // 전역 공유되는 브로커에서만 겉으로 드러나는 문제 - SimpleBroker는
    // 인스턴스별로 격리돼 있어 이 중복이 우연히 안 보였을 뿐).
    @Scheduled(fixedDelayString = "${realtime-price.poll-interval-ms:3000}",
        scheduler = "priceSweepTaskScheduler")
    public void broadcastCurrentPrices() {
        if (!priceRelayLeaderGate.isLeader()) {
            return;
        }
        SafeExecutor.runSafely("실시간 시세 브로드캐스트", this::broadcastOnce);
    }

    private void broadcastOnce() {
        if (!domesticMarketCalendarCache.isMarketOpenNow()) {
            return;
        }

        // 구독자가 한 명도 없는 종목은 메시지를 만들어 보낼 필요도, Redis에서 읽을 필요도 없다
        // (이전엔 관심종목 전체에 대해 구독자 유무와 무관하게 매 틱 발행했다, 2026-10-01).
        List<String> stockCodes = domesticWatchlistedStockCodeCache.get().stream()
            .filter(priceTopicSubscriptionTracker::hasSubscribers)
            .toList();
        if (stockCodes.isEmpty()) {
            return;
        }

        // 관심종목 수만큼 순차 GET을 반복하던 것을 파이프라인 하나로 묶는다
        // (2026-08-19, PriceCacheStore.findAll 참고).
        Map<String, PriceSnapshot> snapshotByStockCode = priceCacheStore.findAll(stockCodes);
        for (String stockCode : stockCodes) {
            PriceSnapshot snapshot = snapshotByStockCode.get(stockCode);
            if (snapshot != null) {
                messagingTemplate.convertAndSend(PRICE_TOPIC_PREFIX + stockCode, snapshot);
            }
        }
    }
}
