package com.quantlab.market.cache;

import com.quantlab.infra.naver.NaverFinanceApiClient;
import com.quantlab.infra.naver.dto.NaverIndexCandleResponse;
import com.quantlab.market.dto.response.IndexChartResponse;
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
 * 해외지수(나스닥/S&P500)·해외 ETF(SOXX) 일봉 이력을 로이터 코드별로 짧은
 * TTL 캐싱한다. 국내 지수(IndexChartCache)와 거의 동일한 패턴이지만,
 * localTradedAt은 "2026-07-14T17:15:59-04:00"처럼 타임존 오프셋이 붙은
 * 전체 일시라 파싱 방식만 다르다(국내는 "2026-07-15" 순수 날짜). SOXX
 * 같은 ETF는 지수가 아니라 종목 엔드포인트로 조회해야 해(WorldIndexCode
 * 참고) isEtf 플래그로 분기한다.
 */
@Component
@RequiredArgsConstructor
public class WorldIndexChartCache {

    private static final int TTL_SECONDS = 60;
    private static final int PAGE_SIZE = 60;

    private final NaverFinanceApiClient naverFinanceApiClient;

    private final Map<String, CacheEntry> cacheByCode = new ConcurrentHashMap<>();

    public List<IndexChartResponse> get(String reutersCode, boolean isEtf) {
        CacheEntry entry = cacheByCode.get(reutersCode);
        if (entry == null || entry.isStale()) {
            entry = refresh(reutersCode, isEtf);
        }
        return entry.candles();
    }

    private synchronized CacheEntry refresh(String reutersCode, boolean isEtf) {
        CacheEntry existing = cacheByCode.get(reutersCode);
        if (existing != null && !existing.isStale()) {
            return existing; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        List<NaverIndexCandleResponse> raw = isEtf
            ? naverFinanceApiClient.getWorldStockPrices(reutersCode, PAGE_SIZE)
            : naverFinanceApiClient.getWorldIndexPrices(reutersCode, PAGE_SIZE);
        List<IndexChartResponse> candles = raw.stream()
            .map(this::toChartResponse)
            .sorted(Comparator.comparing(IndexChartResponse::tradeDate))
            .toList();
        CacheEntry entry = new CacheEntry(candles, Instant.now());
        cacheByCode.put(reutersCode, entry);
        return entry;
    }

    private IndexChartResponse toChartResponse(NaverIndexCandleResponse candle) {
        return new IndexChartResponse(
            OffsetDateTime.parse(candle.localTradedAt()).toLocalDate(),
            parseNumber(candle.openPrice()),
            parseNumber(candle.highPrice()),
            parseNumber(candle.lowPrice()),
            parseNumber(candle.closePrice())
        );
    }

    private double parseNumber(String raw) {
        return Double.parseDouble(raw.replace(",", ""));
    }

    private record CacheEntry(List<IndexChartResponse> candles, Instant cachedAt) {
        boolean isStale() {
            return Duration.between(cachedAt, Instant.now()).getSeconds() >= TTL_SECONDS;
        }
    }
}
