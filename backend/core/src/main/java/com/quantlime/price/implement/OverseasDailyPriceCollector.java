package com.quantlime.price.implement;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.infra.toss.dto.TossPriceMapper;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.dto.DailyCandleSaveResult;
import com.quantlime.price.util.DailyPriceSettlementPolicy;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 해외 종목 일봉을 Toss에서 가져와 {@code overseas_daily_price}에 쌓는 구현 레이어
 * (Implementation) - {@link DomesticDailyPriceCollector}와 같은 구조이고 차이는 저장 대상이
 * {@link OverseasDailyPrice}(Double 가격)라는 점과 재조정 판정 임계값(미국은 가격제한폭이
 * 없어 더 넉넉함)뿐이다. 원래 KIS 기간별시세를 썼으나 Toss `/api/v1/candles`가 해외 티커를
 * 처음부터 지원한다는 게 확인돼(2026-07-29) 국내와 동일한 count/before 커서 페이지네이션으로
 * 통일했다. 재확정 윈도우/수정주가 재조정 감지도 {@link DailyPriceSettlementPolicy}를 공유한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverseasDailyPriceCollector {

    // DomesticDailyPriceCollector.FULL_REBACKFILL_TARGET_DAYS와 동일한 이유
    // (200 = 페이지 크기와 정확히 같아 경계에서 1페이지만 받고 조기 종료하는
    // 문제를 피하기 위함, 2026-08-04 국내에서 실제 발견).
    public static final int FULL_REBACKFILL_TARGET_DAYS = 400;

    private static final int BACKFILL_PAGE_SIZE = 200;

    private final DailyPriceReader dailyPriceReader;
    private final DailyPriceAppender dailyPriceAppender;
    private final TossApiClient tossApiClient;

    /**
     * lookbackDays는 호출측({@code PriceGapFillService})이 "마지막 저장일부터
     * 오늘까지의 실제 갭"만큼만 지정한다(DomesticDailyPriceCollector.refreshRecent와
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
            rebackfill(stockCode, FULL_REBACKFILL_TARGET_DAYS);
        }
    }

    /**
     * 외부 API 왕복과 딜레이가 여러 번 발생할 수 있어 전체를 하나의 트랜잭션으로
     * 묶지 않는다(DomesticDailyPriceCollector.backfillHistory와 동일한 이유).
     */
    public void backfillHistory(String stockCode, int targetDays) {
        long existingCount = dailyPriceReader.countOverseas(stockCode);
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
            // 페이지 사이 별도 sleep 없음 - TossApiClient.getDailyCandles가
            // awaitCandleRateLimit()으로 이미 전역 최소 간격을 강제한다
            // (2026-09-22 제거, RegularCloseBackfillService와 동일한 이유 -
            // 중복 대기가 페이지 수만큼 왕복시간을 낭비시켰다).
            cursor = response.result().nextBefore();
        }

        log.info("해외 이력 백필 완료: stockCode={}, 신규저장={}건", stockCode, savedCount);
    }

    /** {@link DomesticDailyPriceCollector#rebackfill}와 대칭 - 전 구간(또는 지정 개수)을 다시 받아 기존 행을 덮어쓴다. */
    public int rebackfill(String stockCode, int targetDays) {
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
        }

        log.info("해외 수정주가 재백필 완료: stockCode={}, 처리={}건, 신규={}건", stockCode, processed, created);
        return created;
    }

    /**
     * {@link DomesticDailyPriceCollector}의 동명 메서드와 동일한 재확정 윈도우
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
            if (dailyPriceReader.existsOverseas(stockCode, tradeDate)) {
                continue;
            }
            try {
                dailyPriceAppender.saveOverseas(TossPriceMapper.toOverseasDailyPrice(stockCode, candle));
                saved++;
            } catch (DataIntegrityViolationException e) {
                log.debug("해외 이력 백필 중복 저장 스킵: stockCode={}, date={}", stockCode, tradeDate);
            }
        }
        return DailyCandleSaveResult.of(saved, restatementDetected);
    }

    private UpsertOutcome upsertCandle(String stockCode, LocalDate tradeDate,
                                        TossCandleResponse.TossCandle candle, boolean detectRestatement) {
        return dailyPriceReader.findOverseas(stockCode, tradeDate)
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
                dailyPriceAppender.saveOverseas(existing);
                return UpsertOutcome.updated(restated);
            })
            .orElseGet(() -> {
                try {
                    dailyPriceAppender.saveOverseas(TossPriceMapper.toOverseasDailyPrice(stockCode, candle));
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
