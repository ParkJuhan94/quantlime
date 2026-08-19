package com.quantlime.stock.cache;

import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Component;

/**
 * 종목 검색을 위해 전체 종목(8,958행·1.5MB 수준, 2026-08-19 실측)을
 * 메모리에 올려두고 자바에서 필터링한다 - 기존
 * {@code findByStockNameContainingIgnoreCaseOrStockCodeContainingOrKoreanNameContainingIgnoreCase}는
 * 선행 와일드카드 {@code LIKE '%kw%'} 3개 OR라 B-Tree 인덱스로 원천적으로
 * 못 잡아 매 요청 전종목 풀스캔이었다(성능 개선 계획 문서 B3 참고). 종목
 * 마스터는 하루 1회({@code MarketDataRefreshService.refreshAll()})만
 * 갱신되므로 TTL을 길게 잡아도 정확도 손실이 없다.
 *
 * <p>검색 시맨틱은 기존 리포지토리 메서드와 동일하게 유지한다 - 종목명/
 * 한글명은 대소문자 무시, 종목코드는 대소문자 구분(원 메서드명에
 * {@code StockCodeContaining}만 있고 {@code IgnoreCase}가 없었음). 상장폐지
 * 종목도 검색 대상에 포함한다(원 쿼리가 listingStatus로 좁히지 않았음).
 *
 * <p>이 프로젝트의 다른 캐시 10개는 전부 {@code synchronized refresh()}가
 * 갱신 I/O(대개 외부 HTTP 호출)를 요청 스레드에서 직접 기다리는 패턴인데,
 * 그 패턴이 만드는 thundering herd 위험(RELIABILITY.md 참고, 개선은 이후
 * Phase에서 나머지 캐시에도 적용 예정)을 새 캐시에 물려주지 않기 위해
 * 이 캐시는 처음부터 stale-while-revalidate로 만든다: TTL이 지나도 기존
 * 값을 즉시 반환하면서 갱신은 백그라운드로 한 번만 트리거한다. 갱신
 * 대상이 로컬 DB 단순 조회(수 ms)라 위험도 자체는 낮지만, 패턴을 여기서
 * 정착시킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockSearchCache {

    private static final int REFRESH_INTERVAL_SECONDS = 600;

    private final StockRepository stockRepository;

    private volatile List<Stock> cachedStocks = List.of();
    private volatile Instant lastRefreshedAt = Instant.EPOCH;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    public Slice<Stock> search(String keyword, Pageable pageable) {
        ensureWarm();
        if (isStale()) {
            triggerBackgroundRefresh();
        }

        String rawKeyword = keyword.trim();
        String normalizedKeyword = rawKeyword.toLowerCase(Locale.ROOT);
        List<Stock> matched = cachedStocks.stream()
            .filter(stock -> matches(stock, rawKeyword, normalizedKeyword))
            .toList();

        return toSlice(matched, pageable);
    }

    private boolean matches(Stock stock, String rawKeyword, String normalizedKeyword) {
        if (stock.getStockName().toLowerCase(Locale.ROOT).contains(normalizedKeyword)) {
            return true;
        }
        if (stock.getStockCode().contains(rawKeyword)) {
            return true;
        }
        String koreanName = stock.getKoreanName();
        return koreanName != null && koreanName.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private Slice<Stock> toSlice(List<Stock> matched, Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= matched.size()) {
            return new SliceImpl<>(List.of(), pageable, false);
        }
        int end = Math.min(start + pageable.getPageSize(), matched.size());
        boolean hasNext = end < matched.size();
        return new SliceImpl<>(matched.subList(start, end), pageable, hasNext);
    }

    // stale-while-revalidate는 "서빙할 이전 값이 있을 때"만 성립한다 - 기동
    // 직후처럼 캐시가 아예 빈 상태에서 백그라운드 갱신만 트리거하면, 갱신이
    // 끝나기 전까지 들어오는 요청은 빈 결과를 받는다(실제 회귀 시나리오라
    // 별도로 막아둠). 최초 적재 한 번만 동기로 채우고, 그 이후는 전부
    // 백그라운드 경로를 탄다.
    private void ensureWarm() {
        if (cachedStocks.isEmpty()) {
            synchronized (this) {
                if (cachedStocks.isEmpty()) {
                    refresh();
                }
            }
        }
    }

    private boolean isStale() {
        return Duration.between(lastRefreshedAt, Instant.now()).getSeconds() >= REFRESH_INTERVAL_SECONDS;
    }

    private void triggerBackgroundRefresh() {
        if (refreshing.compareAndSet(false, true)) {
            CompletableFuture.runAsync(this::refresh)
                .whenComplete((ignored, ex) -> {
                    refreshing.set(false);
                    if (ex != null) {
                        log.warn("종목 검색 캐시 갱신 실패: error={}", ex.getMessage(), ex);
                    }
                });
        }
    }

    private void refresh() {
        cachedStocks = stockRepository.findAll();
        lastRefreshedAt = Instant.now();
    }
}
