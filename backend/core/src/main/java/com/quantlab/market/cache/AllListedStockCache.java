package com.quantlab.market.cache;

import com.quantlab.stock.domain.ListingStatus;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.repository.StockRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 전체 상장 종목 목록을 짧지 않은 TTL로 캐싱한다. 종목마스터는
 * {@code StockMasterSyncScheduler}가 주 1회만 갱신하므로, 짧은 주기로
 * 도는 {@code MarketPriceSweepScheduler}가 틱마다 2,700여 건을 다시
 * 조회하지 않도록 {@value #REFRESH_INTERVAL_SECONDS}초 동안 재사용한다
 * (WatchlistedStockCodeCache와 동일한 단순 TTL 캐시 패턴).
 */
@Component
@RequiredArgsConstructor
public class AllListedStockCache {

    private static final int REFRESH_INTERVAL_SECONDS = 600;

    private final StockRepository stockRepository;

    private volatile List<Stock> cachedStocks = List.of();
    private volatile Instant lastRefreshedAt = Instant.EPOCH;

    public List<Stock> get() {
        if (isStale()) {
            refresh();
        }
        return cachedStocks;
    }

    private boolean isStale() {
        return Duration.between(lastRefreshedAt, Instant.now()).getSeconds() >= REFRESH_INTERVAL_SECONDS;
    }

    private synchronized void refresh() {
        if (!isStale()) {
            return; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        cachedStocks = stockRepository.findByListingStatus(ListingStatus.LISTED);
        lastRefreshedAt = Instant.now();
    }
}
