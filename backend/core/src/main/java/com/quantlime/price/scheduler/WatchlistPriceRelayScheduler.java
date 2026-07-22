package com.quantlime.price.scheduler;

import com.quantlime.common.util.SafeExecutor;
import com.quantlime.price.cache.MarketCalendarCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.cache.WatchlistedStockCodeCache;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 관심 종목의 최신 시세를 STOMP로 브로드캐스트한다. Toss를 직접 호출하지
 * 않고 {@code MarketPriceSweepScheduler}가 전종목 스윕마다 {@link PriceCacheStore}
 * (Redis)에 적재해둔 스냅샷만 읽어 중계("relay")만 한다 - 두 스케줄러가
 * 각각 Toss를 호출해 관심종목 가격을 중복 조회하던 구조를 단일
 * 파이프라인으로 통합했다({@code MarketPriceSweepScheduler} 참고,
 * 2026-07-15). 이름도 "직접 조회해 브로드캐스트"가 아니라 "캐시를
 * 중계만 한다"는 실제 역할에 맞춰 {@code PriceBroadcastScheduler}에서
 * 변경함(2026-07-16).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchlistPriceRelayScheduler {

    private static final String PRICE_TOPIC_PREFIX = "/topic/price/";

    private final MarketCalendarCache marketCalendarCache;
    private final WatchlistedStockCodeCache watchlistedStockCodeCache;
    private final PriceCacheStore priceCacheStore;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelayString = "${realtime-price.poll-interval-ms:3000}")
    public void broadcastCurrentPrices() {
        SafeExecutor.runSafely("실시간 시세 브로드캐스트", this::broadcastOnce);
    }

    private void broadcastOnce() {
        if (!marketCalendarCache.isMarketOpenNow()) {
            return;
        }

        List<String> stockCodes = watchlistedStockCodeCache.get();
        for (String stockCode : stockCodes) {
            broadcastIfCached(stockCode);
        }
    }

    private void broadcastIfCached(String stockCode) {
        priceCacheStore.find(stockCode)
            .ifPresent(snapshot -> messagingTemplate.convertAndSend(PRICE_TOPIC_PREFIX + stockCode, snapshot));
    }
}
