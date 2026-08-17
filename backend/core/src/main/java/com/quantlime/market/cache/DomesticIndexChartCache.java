package com.quantlime.market.cache;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossMarketIndicatorCandleResponse;
import com.quantlime.market.dto.response.IndexChartResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 지수(코스피/코스닥) 일봉 이력을 지수 코드별로 짧은 TTL 캐싱한다.
 * 장중에도 분 단위로 바뀌지 않는 일봉 데이터라 {@value #TTL_SECONDS}초면
 * 충분하고, 종목 상세 차트와 달리 영속 저장은 하지 않는다.
 *
 * <p>원래 네이버 금융 비공식 API(pageSize 60 상한)를 썼으나, Toss
 * {@code market-indicators/candles}(공식 API, count 상한 200)로 이관했다
 * (2026-07-29, toss-openapi.json 교체 계기로 확인).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomesticIndexChartCache {

    private static final int TTL_SECONDS = 60;
    private static final String INTERVAL_DAILY = "1d";
    // Toss 캔들 count 상한(약 10개월치) - 네이버 pageSize 60 제약이 없어져 확대.
    private static final int PAGE_SIZE = 200;

    private final TossApiClient tossApiClient;

    private final Map<String, CacheEntry> cacheByCode = new ConcurrentHashMap<>();

    public List<IndexChartResponse> get(String indexCode) {
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
        try {
            TossMarketIndicatorCandleResponse response =
                tossApiClient.getMarketIndicatorCandles(indexCode, INTERVAL_DAILY, PAGE_SIZE, null);
            List<TossMarketIndicatorCandleResponse.MarketIndicatorCandle> raw = response.result().candles();
            List<IndexChartResponse> candles = (raw == null ? List.<TossMarketIndicatorCandleResponse.MarketIndicatorCandle>of() : raw)
                .stream()
                .map(this::toChartResponse)
                .sorted(Comparator.comparing(IndexChartResponse::tradeDate))
                .toList();
            CacheEntry entry = new CacheEntry(candles, Instant.now());
            cacheByCode.put(indexCode, entry);
            return entry;
        } catch (Exception e) {
            // stale-serve 패턴(2026-08-17) - 만료된 값이라도 있으면 그대로
            // 반환하고, 아예 없으면(최초 조회) 예외를 그대로 전파한다.
            if (existing == null) {
                throw e;
            }
            log.warn("Toss 국내 지수 일봉 조회 실패, 이전 캐시로 폴백: indexCode={}, error={}",
                indexCode, e.getMessage());
            return existing;
        }
    }

    private IndexChartResponse toChartResponse(TossMarketIndicatorCandleResponse.MarketIndicatorCandle candle) {
        return new IndexChartResponse(
            OffsetDateTime.parse(candle.timestamp()).toLocalDate(),
            Double.parseDouble(candle.openPrice()),
            Double.parseDouble(candle.highPrice()),
            Double.parseDouble(candle.lowPrice()),
            Double.parseDouble(candle.closePrice())
        );
    }

    private record CacheEntry(List<IndexChartResponse> candles, Instant cachedAt) {
        boolean isStale() {
            return Duration.between(cachedAt, Instant.now()).getSeconds() >= TTL_SECONDS;
        }
    }
}
