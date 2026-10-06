package com.quantlime.price.implement;

import com.quantlime.common.util.SleepUtil;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.price.domain.CandleReconcileResult;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.dto.DailyCandleSaveResult;
import com.quantlime.price.dto.mapper.TossPriceMapper;
import com.quantlime.price.util.DailyPriceSettlementPolicy;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 국내 종목 일봉을 Toss에서 가져와 {@code domestic_daily_price}에 쌓는 구현 레이어
 * (Implementation) - 외부 호출·페이지네이션·딜레이·재확정 윈도우 upsert·수정주가
 * 재조정 감지를 이 컴포넌트가 맡고, {@code DomesticDailyPriceService}는 기본 목표·조회
 * 구간 같은 정책과 조회 API만 가진다.
 *
 * <p>쓰기 메서드는 전부 {@code @Transactional}이 아니라 건별로 커밋된다 - 외부 API
 * 왕복과 딜레이가 여러 번 발생해 전체를 하나의 트랜잭션으로 묶으면 DB 커넥션을 오래
 * 쥔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomesticDailyPriceCollector {

    /** 수정주가 전 구간 재백필 목표 - 근거는 {@link #rebackfill} 주석 참고. */
    public static final int FULL_REBACKFILL_TARGET_DAYS = 400;

    private static final int BACKFILL_PAGE_SIZE = 200;
    private static final long BACKFILL_API_DELAY_MS = 150;

    private final DailyPriceReader dailyPriceReader;
    private final DailyPriceAppender dailyPriceAppender;
    private final TossApiClient tossApiClient;

    /**
     * lookbackDays를 고정값이 아니라 호출측이 지정할 수 있게 한 버전 -
     * {@code PriceGapFillService}가 "마지막 저장일부터 오늘까지의 실제 갭"
     * 만큼만 요청해 불필요한 과거 재조회 없이 정확히 그 구간만 채운다.
     * 다만 실제 조회 개수는 {@link DailyPriceSettlementPolicy#MIN_LOOKBACK_CANDLES}
     * 미만으로 내려가지 않는다 - 갭이 0(오늘 행이 이미 있음)이어도 재확정
     * 윈도우 전체를 매번 훑어야 미확정 스냅샷/수정주가 재조정을 놓치지 않는다.
     *
     * <p>레이트리밋 시 {@link TossApiClient#getDailyCandles}가 대기 후
     * 재시도할 수 있어(2026-08-01 클라이언트로 이동 - 이전엔 여기서
     * fetchCandlesWithRetry로 직접 재구현했다) 외부 API 왕복+대기가 여러
     * 번 발생할 수 있으므로 {@link #backfillHistory}와 동일한
     * 이유로 메서드 전체를 트랜잭션으로 묶지 않는다(저장은 건별 커밋).
     */
    public void refreshRecent(String stockCode, int lookbackDays) {
        int count = Math.max(lookbackDays, DailyPriceSettlementPolicy.MIN_LOOKBACK_CANDLES);
        TossCandleResponse response = tossApiClient.getDailyCandles(stockCode, count, null);

        List<TossCandleResponse.TossCandle> candles = response.result().candles();
        if (candles == null || candles.isEmpty()) {
            log.warn("시세 데이터 없음: stockCode={}", stockCode);
            return;
        }

        DailyCandleSaveResult result = saveNewCandles(stockCode, candles, false);
        if (result.savedCount() > 0) {
            log.info("일별 시세 수집 완료: stockCode={}, 신규저장={}건", stockCode, result.savedCount());
        } else {
            log.debug("이미 수집된 데이터: stockCode={}", stockCode);
        }
        if (result.restatementDetected()) {
            log.warn("수정주가 소급 재조정 감지, 전 구간 재백필: stockCode={}", stockCode);
            rebackfill(stockCode, FULL_REBACKFILL_TARGET_DAYS);
        }
    }

    /**
     * 종목의 이력 OHLCV가 목표 일수(목표 일수)에 못 미치면 토스 캔들 조회를
     * 페이지네이션(count=200 + before/nextBefore)으로 반복 호출해 채운다.
     * 이미 충분하면 API를 호출하지 않고 즉시 반환한다.
     *
     * <p>외부 API 왕복과 딜레이가 여러 번 발생할 수 있어 전체를 하나의 트랜잭션으로
     * 묶지 않는다. 저장은 건별로 커밋된다.
     */
    public void backfillHistory(String stockCode, int targetDays) {
        long existingCount = dailyPriceReader.countDomestic(stockCode);
        if (existingCount >= targetDays) {
            log.debug("이력 백필 불필요: stockCode={}, 기존건수={}", stockCode, existingCount);
            return;
        }

        log.info("이력 백필 시작: stockCode={}, 목표={}일, 기존={}건",
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

            if (!SleepUtil.sleepMillis(BACKFILL_API_DELAY_MS)) {
                log.warn("이력 백필 중단: 인터럽트 발생, stockCode={}", stockCode);
                return;
            }
        }

        log.info("이력 백필 완료: stockCode={}, 신규저장={}건", stockCode, savedCount);
    }

    /**
     * 수정주가 소급 재조정(액면분할/병합)이 감지된 종목의 이력을 전 구간
     * (기본 {@link #FULL_REBACKFILL_TARGET_DAYS}) 다시 받아 기존 행을 전부
     * 덮어쓴다. {@link #backfillHistory}와 페이지네이션 구조는
     * 같지만 "이미 충분하면 스킵"과 "존재하면 스킵"이 둘 다 없다 - 재조정은
     * 값이 있는 행 자체가 틀린 사건이라 스킵 조건이 성립하지 않는다.
     *
     * <p>목표를 BACKFILL_TARGET_DAYS(200, 페이지 크기와 정확히
     * 동일)가 아니라 {@link #FULL_REBACKFILL_TARGET_DAYS}(400, 백테스트
     * 유니버스 심화 백필과 동일 관례)로 잡는다 - 200으로 뒀을 때
     * {@code rebackfill}의 루프 조건(`processed &lt;
     * targetDays`)이 정확히 페이지 크기(200)와 같아지는 경계에서, 실제로는
     * 더 받아올 이력이 있는데도(2페이지째 커서가 유효한데도) 1페이지만
     * 받고 종료해버리는 문제가 실제로 발생했다(2026-08-04 실제 복구 런북
     * 실행 중 발견 - 액면분할 시점이 약 210거래일 전이라 정확히 200일
     * 경계를 살짝 넘어서 있었음). 400으로 여유를 둬 이 경계 우연이 실제로
     * 문제가 되는 상황을 피한다.
     */
    public int rebackfill(String stockCode, int targetDays) {
        log.info("수정주가 재백필 시작: stockCode={}, 목표={}일", stockCode, targetDays);
        String cursor = null;
        int processed = 0;
        int created = 0;

        while (processed < targetDays) {
            TossCandleResponse response = tossApiClient.getDailyCandles(stockCode, BACKFILL_PAGE_SIZE, cursor);
            List<TossCandleResponse.TossCandle> candles = response.result().candles();
            if (candles == null || candles.isEmpty()) {
                break;
            }

            // overwriteAll=true - 재조정 감지 자체를 끈다(detectRestatement=false),
            // 안 그러면 이 메서드가 자기 자신을 무한히 다시 호출한다.
            created += saveNewCandles(stockCode, candles, true).savedCount();
            processed += candles.size();

            boolean noMoreHistory = candles.size() < BACKFILL_PAGE_SIZE
                || response.result().nextBefore() == null;
            if (noMoreHistory) {
                break;
            }
            cursor = response.result().nextBefore();

            if (!SleepUtil.sleepMillis(BACKFILL_API_DELAY_MS)) {
                log.warn("수정주가 재백필 중단: 인터럽트 발생, stockCode={}", stockCode);
                return created;
            }
        }

        log.info("수정주가 재백필 완료: stockCode={}, 처리={}건, 신규={}건", stockCode, processed, created);
        return created;
    }

    /**
     * 재확정 윈도우({@link DailyPriceSettlementPolicy#RESETTLEMENT_WINDOW_DAYS})
     * 안의 거래일은 이미 저장돼 있어도 항상 최신 응답으로 덮어쓴다 - Toss
     * 일봉이 NXT를 포함해 20:00까지 계속 갱신되기 때문에(정책 클래스 참고),
     * 그 전에 저장된 값은 정의상 미확정 스냅샷이다. 윈도우 밖 과거 거래일은
     * 기존과 동일하게 존재하면 스킵한다.
     *
     * <p>overwriteAll=true(수정주가 재백필 전용 경로)에서는 윈도우 판정 없이
     * 전부 덮어쓰고, 재조정 감지 자체를 하지 않는다 - 안 그러면 재백필이
     * 자기 자신을 무한히 다시 호출한다.
     */
    private DailyCandleSaveResult saveNewCandles(
            String stockCode, List<TossCandleResponse.TossCandle> candles, boolean overwriteAll) {
        LocalDate today = LocalDate.now();
        int saved = 0;
        boolean restatementDetected = false;
        for (TossCandleResponse.TossCandle candle : candles) {
            LocalDate tradeDate = TossPriceMapper.toLocalDate(candle.timestamp());
            if (overwriteAll || DailyPriceSettlementPolicy.isWithinWindow(tradeDate, today)) {
                UpsertOutcome outcome = upsertCandle(stockCode, tradeDate, candle, overwriteAll);
                if (outcome.created()) {
                    saved++;
                }
                restatementDetected |= outcome.restatementDetected();
                continue;
            }
            if (dailyPriceReader.existsDomestic(stockCode, tradeDate)) {
                continue;
            }
            try {
                dailyPriceAppender.saveDomestic(TossPriceMapper.toDailyPrice(stockCode, candle));
                saved++;
            } catch (DataIntegrityViolationException e) {
                log.debug("이력 백필 중복 저장 스킵: stockCode={}, date={}", stockCode, tradeDate);
            }
        }
        return DailyCandleSaveResult.of(saved, restatementDetected);
    }

    /**
     * 갱신 분기는 dirty checking에 기대지 않고 명시적으로 save()한다 -
     * 이 클래스의 쓰기 메서드는 전부 @Transactional이 아니라 건별로
     * 커밋되므로, findBy로 읽어온 엔티티가 이미 detached 상태일 수 있어
     * 트랜잭션 종료 시 자동 flush를 보장할 수 없다. 기존 행에 캔들을 어떻게 반영할지의
     * 규칙(정규장 종가 보호, 값 동일 시 생략, 재조정 감지)은 {@link DomesticDailyPrice#reconcile}이
     * 갖고 있고, 여기서는 그 결과대로 저장만 한다.
     *
     * @param overwriteAll true면 수정주가 재백필 경로 - 종가 보호를 풀고 재조정 감지를 하지 않는다
     *     (감지하면 재백필이 자기 자신을 무한히 다시 호출한다).
     */
    private UpsertOutcome upsertCandle(String stockCode, LocalDate tradeDate,
                                        TossCandleResponse.TossCandle candle, boolean overwriteAll) {
        return dailyPriceReader.findDomestic(stockCode, tradeDate)
            .map(existing -> {
                CandleReconcileResult result = existing.reconcile(
                    Long.parseLong(candle.openPrice()), Long.parseLong(candle.highPrice()),
                    Long.parseLong(candle.lowPrice()), Long.parseLong(candle.closePrice()),
                    Long.parseLong(candle.volume()), overwriteAll);
                if (result == CandleReconcileResult.UNCHANGED) {
                    return UpsertOutcome.unchanged();
                }
                dailyPriceAppender.saveDomestic(existing);
                return UpsertOutcome.updated(result == CandleReconcileResult.RESTATED);
            })
            .orElseGet(() -> {
                try {
                    dailyPriceAppender.saveDomestic(TossPriceMapper.toDailyPrice(stockCode, candle));
                    return UpsertOutcome.inserted();
                } catch (DataIntegrityViolationException e) {
                    log.debug("당일/재확정 시세 동시 저장 충돌 스킵: stockCode={}, date={}", stockCode, tradeDate);
                    return UpsertOutcome.unchanged();
                }
            });
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
