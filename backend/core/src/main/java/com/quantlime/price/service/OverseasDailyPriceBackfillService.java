package com.quantlime.price.service;

import com.quantlime.price.implement.OverseasDailyPriceCollector;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 해외주식 일별 OHLCV의 수집 정책(재백필 구간 계산)과 진입점 - Toss 호출·페이지네이션·upsert는
 * {@link OverseasDailyPriceCollector}가 맡는다. {@link DomesticDailyPriceService}와 대칭이다.
 */
@Service
@RequiredArgsConstructor
public class OverseasDailyPriceBackfillService {

    // DomesticDailyPriceService.REBACKFILL_BUFFER_DAYS와 동일한 의도.
    private static final int REBACKFILL_BUFFER_DAYS = 5;

    private final OverseasDailyPriceCollector overseasDailyPriceCollector;

    /**
     * lookbackDays는 호출측({@link PriceGapFillService})이 "마지막 저장일부터 오늘까지의 실제
     * 갭"만큼만 지정한다(DomesticDailyPriceService.refreshRecent와 동일한 의도).
     */
    public void refreshRecent(String stockCode, int lookbackDays) {
        overseasDailyPriceCollector.refreshRecent(stockCode, lookbackDays);
    }

    public void backfillHistoryIfNeeded(String stockCode, int targetDays) {
        overseasDailyPriceCollector.backfillHistory(stockCode, targetDays);
    }

    /** {@link DomesticDailyPriceService#rebackfillAdjustedHistory(String)}와 대칭. */
    public int rebackfillAdjustedHistory(String stockCode) {
        return overseasDailyPriceCollector.rebackfill(
            stockCode, OverseasDailyPriceCollector.FULL_REBACKFILL_TARGET_DAYS);
    }

    /** {@link DomesticDailyPriceService#rebackfillAdjustedHistory(String, LocalDate)}와 대칭. */
    public int rebackfillAdjustedHistory(String stockCode, LocalDate from) {
        long calendarGapDays = ChronoUnit.DAYS.between(from, LocalDate.now()) + REBACKFILL_BUFFER_DAYS;
        int targetDays = (int) Math.max(1, calendarGapDays);
        return overseasDailyPriceCollector.rebackfill(stockCode, targetDays);
    }
}
