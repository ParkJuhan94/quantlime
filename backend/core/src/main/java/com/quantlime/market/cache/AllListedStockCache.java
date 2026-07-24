package com.quantlime.market.cache;

import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 전체 "국내" 상장 종목 목록을 짧지 않은 TTL로 캐싱한다. 유일한 소비자인
 * {@code MarketPriceSweepScheduler}가 토스(국내 전용) 현재가 API만 호출하므로
 * {@link MarketType#domesticValues()}로 좁힌다 - KIS 해외 연동(Phase A)으로
 * NASDAQ/NYSE 종목도 같은 {@code stock} 테이블에 LISTED 상태로 함께 들어오게
 * 됐는데, 이 캐시가 그 도입 시점에 함께 갱신되지 않아 한동안 해외 종목까지
 * 토스에 그대로 넘겨졌다 - 스윕당 청크 수가 2,700여 건 기준 설계보다 크게
 * 늘어나 초당 토큰 버킷을 넘겨 429가 스윕 주기(수 초)마다 계속 재발하는
 * 원인이었다(발견 당시 종목마스터 XLS: NASDAQ 3,921 + NYSE 2,443).
 *
 * <p>종목마스터는 {@code StockMasterSyncScheduler}가 주 1회만 갱신하므로,
 * 짧은 주기로 도는 스케줄러가 틱마다 다시 조회하지 않도록
 * {@value #REFRESH_INTERVAL_SECONDS}초 동안 재사용한다(WatchlistedStockCodeCache와
 * 동일한 단순 TTL 캐시 패턴).
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
        cachedStocks = stockRepository.findByListingStatusAndMarketTypeIn(
            ListingStatus.LISTED, MarketType.domesticValues());
        lastRefreshedAt = Instant.now();
    }
}
