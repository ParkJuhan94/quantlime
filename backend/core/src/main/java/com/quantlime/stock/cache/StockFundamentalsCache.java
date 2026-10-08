package com.quantlime.stock.cache;

import com.quantlime.stock.dto.response.StockFundamentalsResponse;
import com.quantlime.stock.implement.StockFundamentalsCollector;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 종목 밸류에이션 지표(시총/PER/PBR/PSR/부채비율)를 종목코드별로 캐싱한다.
 * 재무 데이터는 분기 단위로만 갱신되니 TTL을 {@value #TTL_SECONDS}초(1시간)로
 * 길게 잡는다(실시간 시세 캐시들과는 성격이 다름).
 */
@Component
@RequiredArgsConstructor
public class StockFundamentalsCache {

    private static final int TTL_SECONDS = 3600;

    private final StockFundamentalsCollector stockFundamentalsCollector;

    private final Map<String, CacheEntry> cacheByCode = new ConcurrentHashMap<>();

    public StockFundamentalsResponse get(String stockCode) {
        CacheEntry entry = cacheByCode.get(stockCode);
        if (entry == null || entry.isStale()) {
            entry = refresh(stockCode);
        }
        return entry.response();
    }

    private synchronized CacheEntry refresh(String stockCode) {
        CacheEntry existing = cacheByCode.get(stockCode);
        if (existing != null && !existing.isStale()) {
            return existing; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        StockFundamentalsResponse response = stockFundamentalsCollector.collect(stockCode);
        CacheEntry entry = new CacheEntry(response, Instant.now());
        cacheByCode.put(stockCode, entry);
        return entry;
    }

    private record CacheEntry(StockFundamentalsResponse response, Instant cachedAt) {
        boolean isStale() {
            return Duration.between(cachedAt, Instant.now()).getSeconds() >= TTL_SECONDS;
        }
    }
}
