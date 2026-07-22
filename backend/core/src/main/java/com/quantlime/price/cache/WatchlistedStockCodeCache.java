package com.quantlime.price.cache;

import com.quantlime.watchlist.repository.WatchlistRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 전체 사용자의 관심종목 코드 목록을 짧은 TTL로 캐싱한다. 관심종목
 * 등록/해제는 3초 단위로 일어나는 이벤트가 아니므로, 매 폴링 틱마다
 * DB를 다시 조회하는 대신 최대 {@value #REFRESH_INTERVAL_SECONDS}초의
 * 지연을 감수한다.
 */
@Component
@RequiredArgsConstructor
public class WatchlistedStockCodeCache {

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
        cachedCodes = watchlistRepository.findDistinctStockCodes();
        lastRefreshedAt = Instant.now();
    }
}
