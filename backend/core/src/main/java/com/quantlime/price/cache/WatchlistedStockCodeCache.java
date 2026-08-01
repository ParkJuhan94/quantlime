package com.quantlime.price.cache;

import com.quantlime.stock.domain.MarketType;
import com.quantlime.watchlist.repository.WatchlistRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 전체 사용자의 관심종목 중 특정 시장 코드만 짧은 TTL로 캐싱한다. 관심종목
 * 등록/해제는 3초 단위로 일어나는 이벤트가 아니므로, 매 폴링 틱마다
 * DB를 다시 조회하는 대신 최대 {@value #REFRESH_INTERVAL_SECONDS}초의
 * 지연을 감수한다.
 *
 * <p>국내/해외 모두 이 클래스 하나를 쓴다(2026-08-01 통합 - 이전엔
 * {@code WatchlistedStockCodeCache}/{@code OverseasWatchlistedStockCodeCache}가
 * {@code MarketType.domesticValues()}/{@code overseasValues()} 인자 하나만
 * 다르고 나머지 로직은 완전히 동일한 별개 클래스였다). 시장 범위를 좁혀야
 * 하는 이유는 그대로다 - 국내/해외 시세 파이프라인이 분리돼 있어({@code
 * DomesticWatchlistPriceRelayScheduler} vs {@code OverseasWatchlistPriceScheduler})
 * 이 캐시가 시장 구분 없이 전체를 반환하면 같은 종목이 두 파이프라인에서
 * 중복 브로드캐스트된다. 국내/해외 두 인스턴스는 {@code PriceCacheConfig}가
 * Bean 2개로 등록한다.
 */
public class WatchlistedStockCodeCache {

    private static final int REFRESH_INTERVAL_SECONDS = 30;

    private final WatchlistRepository watchlistRepository;
    private final List<MarketType> marketTypes;

    private volatile List<String> cachedCodes = List.of();
    private volatile Instant lastRefreshedAt = Instant.EPOCH;

    public WatchlistedStockCodeCache(WatchlistRepository watchlistRepository, List<MarketType> marketTypes) {
        this.watchlistRepository = watchlistRepository;
        this.marketTypes = marketTypes;
    }

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
        cachedCodes = watchlistRepository.findDistinctStockCodesByMarketTypeIn(marketTypes);
        lastRefreshedAt = Instant.now();
    }
}
