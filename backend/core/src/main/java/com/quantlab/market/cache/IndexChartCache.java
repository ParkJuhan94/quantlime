package com.quantlab.market.cache;

import com.quantlab.infra.naver.NaverFinanceApiClient;
import com.quantlab.infra.naver.dto.NaverIndexCandleResponse;
import com.quantlab.market.dto.response.IndexChartResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지수(코스피/코스닥) 일봉 이력을 지수 코드별로 짧은 TTL 캐싱한다.
 * 장중에도 분 단위로 바뀌지 않는 일봉 데이터라 {@value #TTL_SECONDS}초면
 * 충분하고, 종목 상세 차트와 달리 영속 저장은 하지 않는다(비공식 API라
 * 계약이 언제 바뀔지 몰라 굳이 DB에 쌓아두지 않음 - 필요하면 그때마다
 * 다시 조회).
 */
@Component
@RequiredArgsConstructor
public class IndexChartCache {

    private static final int TTL_SECONDS = 60;
    // 네이버 금융 비공식 API는 pageSize가 60을 넘으면 400을 반환한다
    // (실제 호출로 확인 - 61 이상은 전부 거부, 60까지는 정상). 약 3개월치
    // 일봉으로 미니 스파크라인·상세 차트 용도엔 충분하다.
    private static final int PAGE_SIZE = 60;

    private final NaverFinanceApiClient naverFinanceApiClient;

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
        List<NaverIndexCandleResponse> raw = naverFinanceApiClient.getIndexPrices(indexCode, PAGE_SIZE);
        List<IndexChartResponse> candles = raw.stream()
            .map(this::toChartResponse)
            .sorted(Comparator.comparing(IndexChartResponse::tradeDate))
            .toList();
        CacheEntry entry = new CacheEntry(candles, Instant.now());
        cacheByCode.put(indexCode, entry);
        return entry;
    }

    private IndexChartResponse toChartResponse(NaverIndexCandleResponse candle) {
        return new IndexChartResponse(
            LocalDate.parse(candle.localTradedAt()),
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
