package com.quantlime.price.cache;

import com.quantlime.stock.domain.MarketType;
import com.quantlime.watchlist.repository.WatchlistRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 전체 사용자의 관심종목 중 해외(NASDAQ/NYSE) 코드만 짧은 TTL로 캐싱한다.
 * {@link WatchlistedStockCodeCache}와 동일한 목적·구조(30초 TTL)이나,
 * 해외 관심종목 실시간가 스케줄러(OverseasWatchlistPriceScheduler) 전용
 * 소스라 시장으로 걸러진 별도 캐시로 둔다 - 기존 캐시는 시장 구분 없이
 * 전체를 반환해 국내 릴레이 스케줄러와 겹치면 같은 종목이 두 번
 * 브로드캐스트될 수 있다.
 */
@Component
@RequiredArgsConstructor
public class OverseasWatchlistedStockCodeCache {

    private static final int REFRESH_INTERVAL_SECONDS = 30;

    private final WatchlistRepository watchlistRepository;

    private volatile List<String> cachedCodes = List.of();
    private volatile Instant lastRefreshedAt = Instant.EPOCH;

    public List<String> get() {
        if (isStale()) {
            refresh();
        }
        return cachedCodes;
    }

    private boolean isStale() {
        return Duration.between(lastRefreshedAt, Instant.now()).getSeconds() >= REFRESH_INTERVAL_SECONDS;
    }

    private synchronized void refresh() {
        if (!isStale()) {
            return; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        cachedCodes = watchlistRepository.findDistinctStockCodesByMarketTypeIn(MarketType.overseasValues());
        lastRefreshedAt = Instant.now();
    }
}
