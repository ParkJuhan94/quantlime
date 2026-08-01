package com.quantlime.price.cache;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 국내/해외 전용 조회 로직({@link com.quantlime.price.config.PriceCacheConfig}
 * 참고)과 분리된, 캐싱 제어 흐름(날짜/신규코드 기준 재조회 판단)만 검증한다
 * (2026-08-01 국내/해외 클래스 통합 - 이전엔 이 시나리오를
 * PreviousCloseCacheTest/OverseasPreviousCloseCacheTest 두 파일에서 값
 * 타입만 다르게 복붙 검증했다). priceFetcher는 실제 리포지토리 대신 호출
 * 횟수를 세는 간단한 함수로 대체해, 국내/해외 어느 쪽에도 종속되지 않는다.
 */
@Tag("unit")
class PreviousCloseCacheTest {

    private static final String STOCK_CODE = "005930";

    @Test
    @DisplayName("[첫 조회 시 배치 조회로 전일 종가를 가져온다]")
    void get_firstCall_fetchesBatch() {
        // given
        AtomicInteger callCount = new AtomicInteger();
        PreviousCloseCache cache = cacheReturning(callCount, Map.of(STOCK_CODE, 70000.0));

        // when
        Map<String, Double> result = cache.get(List.of(STOCK_CODE));

        // then
        assertThat(result).containsEntry(STOCK_CODE, 70000.0);
        assertThat(callCount).hasValue(1);
    }

    @Test
    @DisplayName("[같은 날 같은 종목을 다시 조회하면 배치 조회를 반복하지 않는다]")
    void get_sameDaySameCodes_doesNotRefetch() {
        // given
        AtomicInteger callCount = new AtomicInteger();
        PreviousCloseCache cache = cacheReturning(callCount, Map.of(STOCK_CODE, 70000.0));

        // when
        cache.get(List.of(STOCK_CODE));
        cache.get(List.of(STOCK_CODE));

        // then
        assertThat(callCount).hasValue(1);
    }

    @Test
    @DisplayName("[캐시에 없는 신규 종목이 섞이면 그 시점에 다시 조회한다]")
    void get_newCodeNotCached_refetches() {
        // given: 첫 조회는 기존 종목만 반환(신규 종목은 아직 캐시에 없는
        // 상태를 재현) - 두 번째 조회에서 신규 종목이 섞이면 그때 캐시가
        // 신규 종목을 포함한 전체 결과로 다시 채워진다
        String newCode = "000660";
        AtomicInteger callCount = new AtomicInteger();
        BiFunction<List<String>, LocalDate, Map<String, Double>> fetcher = (codes, date) ->
            callCount.incrementAndGet() == 1
                ? Map.of(STOCK_CODE, 70000.0)
                : Map.of(STOCK_CODE, 70000.0, newCode, 50000.0);
        PreviousCloseCache cache = new PreviousCloseCache(fetcher);

        // when: 처음엔 기존 종목만, 두 번째엔 캐시에 없는 신규 종목이 섞임
        cache.get(List.of(STOCK_CODE));
        Map<String, Double> result = cache.get(List.of(STOCK_CODE, newCode));

        // then
        assertThat(result).containsEntry(newCode, 50000.0);
        assertThat(callCount).hasValue(2);
    }

    @Test
    @DisplayName("[날짜가 바뀌면 같은 종목이어도 다시 조회한다]")
    void get_dateChanged_refetches() {
        // given
        AtomicInteger callCount = new AtomicInteger();
        PreviousCloseCache cache = cacheReturning(callCount, Map.of(STOCK_CODE, 71000.0));
        cache.get(List.of(STOCK_CODE));

        // when: 캐시된 날짜를 어제로 되돌려 "날짜가 바뀐" 상태를 재현
        ReflectionTestUtils.setField(cache, "cachedDate", LocalDate.now().minusDays(1));
        Map<String, Double> result = cache.get(List.of(STOCK_CODE));

        // then
        assertThat(result).containsEntry(STOCK_CODE, 71000.0);
        assertThat(callCount).hasValue(2);
    }

    private PreviousCloseCache cacheReturning(AtomicInteger callCount, Map<String, Double> fixedResult) {
        BiFunction<List<String>, LocalDate, Map<String, Double>> fetcher = (codes, date) -> {
            callCount.incrementAndGet();
            return fixedResult;
        };
        return new PreviousCloseCache(fetcher);
    }
}
