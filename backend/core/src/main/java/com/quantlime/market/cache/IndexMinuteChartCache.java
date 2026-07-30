package com.quantlime.market.cache;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossMarketIndicatorCandleResponse;
import com.quantlime.market.dto.response.IndexMinuteChartResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지수(코스피/코스닥) 당일 1분봉을 지수 코드별로 짧은 TTL 캐싱한다. 장중엔
 * 분 단위로 계속 바뀌는 데이터라 {@value #TTL_SECONDS}초로 IndexChartCache
 * (일봉, 60초)보다 짧게 잡는다.
 *
 * <p>원래 네이버 금융 비공식 API를 썼으나, Toss
 * {@code market-indicators/candles}(interval=1m, 공식 API)로 이관했다
 * (2026-07-29). 단, Toss는 count 상한이 200이라(국내 정규장 390분보다
 * 적음) 당일 전체가 아니라 최근 200분만 커버한다 - 20초 TTL로 장중에
 * 자주 갱신되는 캐시라 `before` 커서로 추가 페이지를 매번 더 부르면 Toss
 * 호출량이 급증해, 최근 구간만 보여주는 쪽을 택했다(미니 차트 용도라
 * 실용적 트레이드오프로 판단).
 */
@Component
@RequiredArgsConstructor
public class IndexMinuteChartCache {

    private static final int TTL_SECONDS = 20;
    private static final String INTERVAL_MINUTE = "1m";
    private static final int PAGE_SIZE = 200;

    private final TossApiClient tossApiClient;

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
        TossMarketIndicatorCandleResponse response =
            tossApiClient.getMarketIndicatorCandles(indexCode, INTERVAL_MINUTE, PAGE_SIZE, null);
        List<TossMarketIndicatorCandleResponse.MarketIndicatorCandle> raw = response.result().candles();
        List<IndexMinuteChartResponse> candles = (raw == null ? List.<TossMarketIndicatorCandleResponse.MarketIndicatorCandle>of() : raw)
            .stream()
            .map(this::toChartResponse)
            .sorted(Comparator.comparing(IndexMinuteChartResponse::time))
            .toList();
        CacheEntry entry = new CacheEntry(candles, Instant.now());
        cacheByCode.put(indexCode, entry);
        return entry;
    }

    private IndexMinuteChartResponse toChartResponse(TossMarketIndicatorCandleResponse.MarketIndicatorCandle candle) {
        return new IndexMinuteChartResponse(
            OffsetDateTime.parse(candle.timestamp()).toLocalDateTime(),
            Double.parseDouble(candle.closePrice())
        );
    }

    private record CacheEntry(List<IndexMinuteChartResponse> candles, Instant cachedAt) {
        boolean isStale() {
            return Duration.between(cachedAt, Instant.now()).getSeconds() >= TTL_SECONDS;
        }
    }
}
