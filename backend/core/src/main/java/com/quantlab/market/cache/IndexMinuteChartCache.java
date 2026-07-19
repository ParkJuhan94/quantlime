package com.quantlab.market.cache;

import com.quantlab.infra.naver.NaverFinanceApiClient;
import com.quantlab.infra.naver.dto.NaverIndexMinuteCandleResponse;
import com.quantlab.market.dto.response.IndexMinuteChartResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지수(코스피/코스닥) 당일 1분봉을 지수 코드별로 짧은 TTL 캐싱한다. 장중엔
 * 분 단위로 계속 바뀌는 데이터라 {@value #TTL_SECONDS}초로 IndexChartCache
 * (일봉, 60초)보다 짧게 잡는다 - MarketIndexCache(환율/비트코인, 20초)와
 * 동일한 갱신감을 노린다.
 */
@Component
@RequiredArgsConstructor
public class IndexMinuteChartCache {

    private static final int TTL_SECONDS = 20;
    private static final DateTimeFormatter SOURCE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final NaverFinanceApiClient naverFinanceApiClient;

    private final Map<String, CacheEntry> cacheByCode = new ConcurrentHashMap<>();

    public List<IndexMinuteChartResponse> get(String indexCode) {
        CacheEntry entry = cacheByCode.get(indexCode);
        if (entry == null || entry.isStale()) {
            entry = refresh(indexCode);
        }
        return entry.candles();
    }

    private synchronized CacheEntry refresh(String indexCode) {
        CacheEntry existing = cacheByCode.get(indexCode);
        if (existing != null && !existing.isStale()) {
            return existing; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        List<NaverIndexMinuteCandleResponse> raw = naverFinanceApiClient.getIndexMinuteCandles(indexCode);
        List<IndexMinuteChartResponse> candles = raw.stream()
            .map(this::toChartResponse)
            .sorted(Comparator.comparing(IndexMinuteChartResponse::time))
            .toList();
        CacheEntry entry = new CacheEntry(candles, Instant.now());
        cacheByCode.put(indexCode, entry);
        return entry;
    }

    private IndexMinuteChartResponse toChartResponse(NaverIndexMinuteCandleResponse candle) {
        return new IndexMinuteChartResponse(
            LocalDateTime.parse(candle.localDateTime(), SOURCE_FORMAT),
            candle.currentPrice()
        );
    }

    private record CacheEntry(List<IndexMinuteChartResponse> candles, Instant cachedAt) {
        boolean isStale() {
            return Duration.between(cachedAt, Instant.now()).getSeconds() >= TTL_SECONDS;
        }
    }
}
