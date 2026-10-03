package com.quantlime.price.service;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.implement.DailyPriceReader;
import com.quantlime.price.implement.DomesticDailyPriceCollector;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 국내 일봉의 수집 정책(기본 목표·조회 구간)과 조회 API - Toss 호출·페이지네이션·upsert는
 * {@link DomesticDailyPriceCollector}가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class DomesticDailyPriceService {

    private static final int BACKFILL_TARGET_DAYS = 200;
    // 배치가 하루 이상 못 돈 날(로컬 개발 서버 다운타임, 공휴일 스케줄 밀림
    // 등)에도 다음 실행에서 자동으로 따라잡을 수 있도록 "최신 1건"이 아니라
    // 최근 며칠치를 함께 조회한다.
    private static final int DAILY_COLLECT_LOOKBACK_DAYS = 10;
    // rebackfillAdjustedHistory(stockCode, from)이 "from까지 확실히 포함되게"
    // 캔들 개수를 역산할 때 붙이는 여유분 - PriceGapFillService.GAP_BUFFER_DAYS와
    // 동일한 의도(주말/휴장일로 거래일수가 달력일수보다 적으므로).
    private static final int REBACKFILL_BUFFER_DAYS = 5;

    private final DailyPriceReader dailyPriceReader;
    private final DomesticDailyPriceCollector domesticDailyPriceCollector;

    public void refreshRecent(String stockCode) {
        refreshRecent(stockCode, DAILY_COLLECT_LOOKBACK_DAYS);
    }

    /**
     * lookbackDays를 고정값이 아니라 호출측이 지정할 수 있게 한 버전 -
     * {@link PriceGapFillService}가 "마지막 저장일부터 오늘까지의 실제 갭"만큼만 요청해
     * 불필요한 과거 재조회 없이 정확히 그 구간만 채운다.
     */
    public void refreshRecent(String stockCode, int lookbackDays) {
        domesticDailyPriceCollector.refreshRecent(stockCode, lookbackDays);
    }

    @Transactional(readOnly = true)
    public List<DomesticDailyPrice> getDailyPrices(String stockCode, LocalDate start, LocalDate end) {
        return dailyPriceReader
            .findDomesticBetween(stockCode, start, end);
    }

    /**
     * 여러 종목의 이력을 한 번의 쿼리로 조회한다. 종목 수만큼 순차 조회하는
     * N+1 패턴을 피하기 위한 배치 버전 - 호출 측에서 stockCode별로 그룹핑해
     * 사용한다.
     */
    @Transactional(readOnly = true)
    public List<DomesticDailyPrice> getDailyPrices(List<String> stockCodes, LocalDate start, LocalDate end) {
        if (stockCodes.isEmpty()) {
            return List.of();
        }
        return dailyPriceReader
            .findDomesticBetweenForCodes(stockCodes, start, end);
    }

    /** 종목의 이력 OHLCV가 목표 일수(기본 200일)에 못 미치면 Toss 캔들을 페이지네이션으로 채운다. */
    public void backfillHistoryIfNeeded(String stockCode) {
        backfillHistoryIfNeeded(stockCode, BACKFILL_TARGET_DAYS);
    }

    public void backfillHistoryIfNeeded(String stockCode, int targetDays) {
        domesticDailyPriceCollector.backfillHistory(stockCode, targetDays);
    }

    /**
     * 수정주가 소급 재조정(액면분할/병합)이 감지된 종목의 이력을 전 구간(400일) 다시 받아
     * 기존 행을 전부 덮어쓴다.
     */
    public int rebackfillAdjustedHistory(String stockCode) {
        return domesticDailyPriceCollector.rebackfill(
            stockCode, DomesticDailyPriceCollector.FULL_REBACKFILL_TARGET_DAYS);
    }

    /**
     * from이 주어지면 "달력일 갭 + 여유분"만큼의 캔들 개수만 요청해 페이지
     * 수를 최소화한다 - 복구 시나리오({@code DailyPriceIntegrityService})에서
     * 결정적으로 쓰인다.
     */
    public int rebackfillAdjustedHistory(String stockCode, LocalDate from) {
        long calendarGapDays = ChronoUnit.DAYS.between(from, LocalDate.now()) + REBACKFILL_BUFFER_DAYS;
        int targetDays = (int) Math.max(1, calendarGapDays);
        return domesticDailyPriceCollector.rebackfill(stockCode, targetDays);
    }
}
