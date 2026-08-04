package com.quantlime.price.service;

import com.quantlime.common.util.SleepUtil;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.infra.toss.dto.TossPriceMapper;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.dto.DailyCandleSaveResult;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.util.DailyPriceSettlementPolicy;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 해외주식 일별 OHLCV 백필. 원래 KIS(한국투자증권) 기간별시세 API를 썼으나,
 * Toss `/api/v1/candles`가 처음부터 해외 티커를 지원한다는 게 확인되면서
 * (2026-07-29 세션, `toss-openapi.json` 교체 계기로 라이브 재검증) KIS
 * 연동을 걷어내고 국내와 동일한 count/before 커서 페이지네이션으로
 * 통일했다 - 구조는 {@link DomesticDailyPriceService}의 백필과 완전히 동일하고,
 * 차이는 저장 대상이 {@link com.quantlime.price.domain.OverseasDailyPrice}
 * (Double 가격)라는 점뿐이다. 재확정 윈도우/수정주가 재조정 감지도
 * {@link DomesticDailyPriceService}와 동일한 정책({@link DailyPriceSettlementPolicy})을
 * 공유한다 - 단 미국은 가격제한폭이 없어 재조정 판정 임계값만 더 넉넉하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasDailyPriceBackfillService {

    private static final int BACKFILL_TARGET_DAYS = 200;
    private static final int BACKFILL_PAGE_SIZE = 200;
    private static final long API_DELAY_MS = 150;
    // DomesticDailyPriceService.REBACKFILL_BUFFER_DAYS와 동일한 의도.
    private static final int REBACKFILL_BUFFER_DAYS = 5;
    // DomesticDailyPriceService.FULL_REBACKFILL_TARGET_DAYS와 동일한 이유
    // (200 = 페이지 크기와 정확히 같아 경계에서 1페이지만 받고 조기 종료하는
    // 문제를 피하기 위함, 2026-08-04 국내에서 실제 발견).
    private static final int FULL_REBACKFILL_TARGET_DAYS = 400;

    private final OverseasDailyPriceRepository overseasDailyPriceRepository;
    private final TossApiClient tossApiClient;

    /**
     * lookbackDays는 호출측({@link PriceGapFillService})이 "마지막 저장일부터
     * 오늘까지의 실제 갭"만큼만 지정한다(DomesticDailyPriceService.refreshRecent와
     * 동일한 의도). {@code fillOverseasGap}의 rawGapDays<=0 조기 반환이 없어
     * (trade_date가 US 로컬 날짜라 KST 기준 오늘 대비 갭이 사실상 항상
     * 1 이상이라 매 스윕 재조회된다) 국내처럼 별도 "확정 여부" 판정을
     * 앞단에 둘 필요는 없지만, 저장측은 국내와 동일하게 재확정 윈도우를
     * 적용해 그 재조회를 실제 덮어쓰기로 연결한다.
     */
    public void refreshRecent(String stockCode, int lookbackDays) {
        int count = Math.max(lookbackDays, DailyPriceSettlementPolicy.MIN_LOOKBACK_CANDLES);
        TossCandleResponse response = tossApiClient.getDailyCandles(stockCode, count, null);
        List<TossCandleResponse.TossCandle> candles = response.result().candles();
        if (candles == null || candles.isEmpty()) {
            log.warn("해외 시세 데이터 없음: stockCode={}", stockCode);
            return;
        }

        DailyCandleSaveResult result = saveNewCandles(stockCode, candles, false);
        if (result.savedCount() > 0) {
            log.info("해외 일별 시세 수집 완료: stockCode={}, 신규저장={}건", stockCode, result.savedCount());
        } else {
            log.debug("이미 수집된 해외 시세: stockCode={}", stockCode);
        }
        if (result.restatementDetected()) {
            log.warn("해외 수정주가 소급 재조정 감지, 전 구간 재백필: stockCode={}", stockCode);
            rebackfillAdjustedHistory(stockCode);
        }
    }

    /**
     * 외부 API 왕복과 딜레이가 여러 번 발생할 수 있어 전체를 하나의 트랜잭션으로
     * 묶지 않는다(DomesticDailyPriceService.backfillHistoryIfNeeded와 동일한 이유).
     */
    public void backfillHistoryIfNeeded(String stockCode, int targetDays) {
        long existingCount = overseasDailyPriceRepository.countByStockCode(stockCode);
        if (existingCount >= targetDays) {
            log.debug("해외 이력 백필 불필요: stockCode={}, 기존건수={}", stockCode, existingCount);
            return;
        }

        log.info("해외 이력 백필 시작: stockCode={}, 목표={}일, 기존={}건",
            stockCode, targetDays, existingCount);

        String cursor = null;
        int savedCount = 0;

        while (savedCount < targetDays) {
            TossCandleResponse response = tossApiClient.getDailyCandles(stockCode, BACKFILL_PAGE_SIZE, cursor);
            List<TossCandleResponse.TossCandle> candles = response.result().candles();
            if (candles == null || candles.isEmpty()) {
                break;
            }

            savedCount += saveNewCandles(stockCode, candles, false).savedCount();

            boolean noMoreHistory = candles.size() < BACKFILL_PAGE_SIZE
                || response.result().nextBefore() == null;
            if (noMoreHistory) {
                break;
            }
            cursor = response.result().nextBefore();

            if (!SleepUtil.sleepMillis(API_DELAY_MS)) {
                log.warn("해외 이력 백필 중단: 인터럽트 발생, stockCode={}", stockCode);
                return;
            }
        }

        log.info("해외 이력 백필 완료: stockCode={}, 신규저장={}건", stockCode, savedCount);
    }

    /** {@link DomesticDailyPriceService#rebackfillAdjustedHistory(String)}와 대칭. */
    public int rebackfillAdjustedHistory(String stockCode) {
        return rebackfillAdjustedHistoryUntil(stockCode, FULL_REBACKFILL_TARGET_DAYS);
    }

    /** {@link DomesticDailyPriceService#rebackfillAdjustedHistory(String, LocalDate)}와 대칭. */
    public int rebackfillAdjustedHistory(String stockCode, LocalDate from) {
        long calendarGapDays = ChronoUnit.DAYS.between(from, LocalDate.now()) + REBACKFILL_BUFFER_DAYS;
        int targetDays = (int) Math.max(1, calendarGapDays);
        return rebackfillAdjustedHistoryUntil(stockCode, targetDays);
    }

    private int rebackfillAdjustedHistoryUntil(String stockCode, int targetDays) {
        log.info("해외 수정주가 재백필 시작: stockCode={}, 목표={}일", stockCode, targetDays);
        String cursor = null;
        int processed = 0;
        int created = 0;

        while (processed < targetDays) {
            TossCandleResponse response = tossApiClient.getDailyCandles(stockCode, BACKFILL_PAGE_SIZE, cursor);
            List<TossCandleResponse.TossCandle> candles = response.result().candles();
            if (candles == null || candles.isEmpty()) {
                break;
            }

            created += saveNewCandles(stockCode, candles, true).savedCount();
            processed += candles.size();

            boolean noMoreHistory = candles.size() < BACKFILL_PAGE_SIZE
                || response.result().nextBefore() == null;
            if (noMoreHistory) {
                break;
            }
            cursor = response.result().nextBefore();

            if (!SleepUtil.sleepMillis(API_DELAY_MS)) {
                log.warn("해외 수정주가 재백필 중단: 인터럽트 발생, stockCode={}", stockCode);
                return created;
            }
        }

        log.info("해외 수정주가 재백필 완료: stockCode={}, 처리={}건, 신규={}건", stockCode, processed, created);
        return created;
    }

    /**
     * {@link DomesticDailyPriceService}의 동명 메서드와 동일한 재확정 윈도우
     * 정책을 쓴다. 국내와의 유일한 차이는 재조정 판정 임계값(미국은 가격제한폭이
     * 없어 더 넉넉함)과 가격 필드 타입(Double)이다.
     */
    private DailyCandleSaveResult saveNewCandles(
            String stockCode, List<TossCandleResponse.TossCandle> candles, boolean overwriteAll) {
        LocalDate today = LocalDate.now();
        int saved = 0;
        boolean restatementDetected = false;
        for (TossCandleResponse.TossCandle candle : candles) {
            LocalDate tradeDate = TossPriceMapper.toLocalDate(candle.timestamp());
            if (overwriteAll || DailyPriceSettlementPolicy.isWithinWindow(tradeDate, today)) {
                UpsertOutcome outcome = upsertCandle(stockCode, tradeDate, candle, !overwriteAll);
                if (outcome.created()) {
                    saved++;
                }
                restatementDetected |= outcome.restatementDetected();
                continue;
            }
            if (overseasDailyPriceRepository.existsByStockCodeAndTradeDate(stockCode, tradeDate)) {
                continue;
            }
            try {
                overseasDailyPriceRepository.save(TossPriceMapper.toOverseasDailyPrice(stockCode, candle));
                saved++;
            } catch (DataIntegrityViolationException e) {
                log.debug("해외 이력 백필 중복 저장 스킵: stockCode={}, date={}", stockCode, tradeDate);
            }
        }
        return DailyCandleSaveResult.of(saved, restatementDetected);
    }

    private UpsertOutcome upsertCandle(String stockCode, LocalDate tradeDate,
                                        TossCandleResponse.TossCandle candle, boolean detectRestatement) {
        return overseasDailyPriceRepository.findByStockCodeAndTradeDate(stockCode, tradeDate)
            .map(existing -> {
                double open = Double.parseDouble(candle.openPrice());
                double high = Double.parseDouble(candle.highPrice());
                double low = Double.parseDouble(candle.lowPrice());
                double close = Double.parseDouble(candle.closePrice());
                long volume = Long.parseLong(candle.volume());
                if (isUnchanged(existing, open, high, low, close, volume)) {
                    return UpsertOutcome.unchanged();
                }
                boolean restated = detectRestatement && DailyPriceSettlementPolicy.isRestatement(
                    existing.getClosePrice(), close, DailyPriceSettlementPolicy.OVERSEAS_RESTATEMENT_THRESHOLD);
                existing.updateOhlcv(open, high, low, close, volume);
                overseasDailyPriceRepository.save(existing);
                return UpsertOutcome.updated(restated);
            })
            .orElseGet(() -> {
                try {
                    overseasDailyPriceRepository.save(TossPriceMapper.toOverseasDailyPrice(stockCode, candle));
                    return UpsertOutcome.inserted();
                } catch (DataIntegrityViolationException e) {
                    log.debug("해외 당일/재확정 시세 동시 저장 충돌 스킵: stockCode={}, date={}", stockCode, tradeDate);
                    return UpsertOutcome.unchanged();
                }
            });
    }

    private boolean isUnchanged(OverseasDailyPrice existing, double open, double high,
                                double low, double close, long volume) {
        return existing.getOpenPrice() == open && existing.getHighPrice() == high
            && existing.getLowPrice() == low && existing.getClosePrice() == close
            && existing.getVolume() == volume;
    }

    private record UpsertOutcome(boolean created, boolean restatementDetected) {

        static UpsertOutcome unchanged() {
            return new UpsertOutcome(false, false);
        }

        static UpsertOutcome updated(boolean restatementDetected) {
            return new UpsertOutcome(false, restatementDetected);
        }

        static UpsertOutcome inserted() {
            return new UpsertOutcome(true, false);
        }
    }
}
