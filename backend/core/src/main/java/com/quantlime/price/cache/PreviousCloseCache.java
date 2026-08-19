package com.quantlime.price.cache;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 종목별 전일 종가를 캘린더 날짜 기준으로 캐싱한다. 전일 종가는 당일
 * 하루 동안 값이 바뀌지 않으므로 배치 조회는 하루 1회만 수행한다. 단,
 * 캐시된 이후 새로 관심 종목에 등록된 종목처럼 캐시에 없는 코드가
 * 섞여 있으면 그 시점에만 다시 조회한다.
 *
 * <p>국내/해외 모두 이 클래스 하나를 쓴다(2026-08-01 통합 - 이전엔
 * {@code PreviousCloseCache}/{@code OverseasPreviousCloseCache}가 반환
 * 타입(Long/Double)과 조회 리포지토리만 다르고 제어 흐름은 문자 단위로
 * 동일한 별개 클래스였다). 조회 방식 차이는 생성자로 주입받는
 * {@code priceFetcher}로 흡수한다 - 국내는 {@code Long}(원화 정수값)을
 * {@code Double}로 변환해서, 해외는 이미 {@code Double}(USD)을 그대로
 * 반환한다(값 손실 없음). 국내/해외 두 인스턴스는 {@code PriceCacheConfig}가
 * Bean 2개로 등록한다.
 */
public class PreviousCloseCache {

    private final BiFunction<List<String>, LocalDate, Map<String, Double>> priceFetcher;

    private volatile Map<String, Double> closeByStockCode = Map.of();
    // needsRefresh가 "값이 있는 코드"가 아니라 "이미 시도해본 코드"를 기준으로
    // 판단하게 하는 필드(2026-08-19 수렴 버그 수정). 이전엔
    // closeByStockCode.keySet()으로 판단했는데, 가격 행이 아예 없는 종목
    // (신규상장 직후 등)이 하나라도 섞이면 그 종목은 priceFetcher가 영원히
    // 반환하지 않으므로 containsAll이 영구히 false가 되어, 100ms 주기 스윕이
    // 매 틱마다 배치 조회를 재실행했다(2,596종목 IN절 기준 60초 이상 실측,
    // `docs/LOAD_TESTING.md`/plan 문서 B2 참고).
    private volatile Set<String> triedStockCodes = Set.of();
    private volatile LocalDate cachedDate = LocalDate.MIN;

    public PreviousCloseCache(BiFunction<List<String>, LocalDate, Map<String, Double>> priceFetcher) {
        this.priceFetcher = priceFetcher;
    }

    public Map<String, Double> get(List<String> stockCodes) {
        LocalDate today = LocalDate.now();
        if (needsRefresh(stockCodes, today)) {
            refresh(stockCodes, today);
        }
        return closeByStockCode;
    }

    private boolean needsRefresh(List<String> stockCodes, LocalDate today) {
        return !cachedDate.equals(today) || !triedStockCodes.containsAll(stockCodes);
    }

    private synchronized void refresh(List<String> stockCodes, LocalDate today) {
        if (!needsRefresh(stockCodes, today)) {
            return; // 락 대기 중 다른 스레드가 이미 갱신함
        }
        closeByStockCode = priceFetcher.apply(stockCodes, today);
        triedStockCodes = Set.copyOf(stockCodes);
        cachedDate = today;
    }
}
