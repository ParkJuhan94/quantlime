package com.quantlime.price.service;

import com.quantlime.common.util.SleepUtil;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.infra.toss.dto.TossPriceMapper;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.dto.DailyCandleSaveResult;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.util.DailyPriceSettlementPolicy;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomesticDailyPriceService {

    private static final int BACKFILL_TARGET_DAYS = 200;
    private static final int BACKFILL_PAGE_SIZE = 200;
    private static final long BACKFILL_API_DELAY_MS = 150;
    // 배치가 하루 이상 못 돈 날(로컬 개발 서버 다운타임, 공휴일 스케줄 밀림
    // 등)에도 다음 실행에서 자동으로 따라잡을 수 있도록 "최신 1건"이 아니라
    // 최근 며칠치를 함께 조회한다.
    private static final int DAILY_COLLECT_LOOKBACK_DAYS = 10;
    // rebackfillAdjustedHistory(stockCode, from)이 "from까지 확실히 포함되게"
    // 캔들 개수를 역산할 때 붙이는 여유분 - PriceGapFillService.GAP_BUFFER_DAYS와
    // 동일한 의도(주말/휴장일로 거래일수가 달력일수보다 적으므로).
    private static final int REBACKFILL_BUFFER_DAYS = 5;
    // rebackfillAdjustedHistory(stockCode)(from 미지정, "감지 즉시 전 구간
    // 재백필") 전용 목표 - 백테스트 유니버스 심화 백필(DomesticUniverseSelectionService)과
    // 동일하게 400일로 잡는다. 근거는 위 rebackfillAdjustedHistory(String)
    // 주석 참고.
    private static final int FULL_REBACKFILL_TARGET_DAYS = 400;

    private final DomesticDailyPriceRepository domesticDailyPriceRepository;
    private final TossApiClient tossApiClient;

    public void refreshRecent(String stockCode) {
        refreshRecent(stockCode, DAILY_COLLECT_LOOKBACK_DAYS);
    }

    /**
     * lookbackDays를 고정값이 아니라 호출측이 지정할 수 있게 한 버전 -
     * {@link PriceGapFillService}가 "마지막 저장일부터 오늘까지의 실제 갭"
     * 만큼만 요청해 불필요한 과거 재조회 없이 정확히 그 구간만 채운다.
     * 다만 실제 조회 개수는 {@link DailyPriceSettlementPolicy#MIN_LOOKBACK_CANDLES}
     * 미만으로 내려가지 않는다 - 갭이 0(오늘 행이 이미 있음)이어도 재확정
     * 윈도우 전체를 매번 훑어야 미확정 스냅샷/수정주가 재조정을 놓치지 않는다.
     *
     * <p>레이트리밋 시 {@link TossApiClient#getDailyCandles}가 대기 후
     * 재시도할 수 있어(2026-08-01 클라이언트로 이동 - 이전엔 여기서
     * fetchCandlesWithRetry로 직접 재구현했다) 외부 API 왕복+대기가 여러
     * 번 발생할 수 있으므로 {@link #backfillHistoryIfNeeded}와 동일한
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
            rebackfillAdjustedHistory(stockCode);
        }
    }

    @Transactional(readOnly = true)
    public List<DomesticDailyPrice> getDailyPrices(String stockCode, LocalDate start, LocalDate end) {
        return domesticDailyPriceRepository
            .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, start, end);
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
        return domesticDailyPriceRepository
            .findByStockCodeInAndTradeDateBetweenOrderByTradeDateDesc(stockCodes, start, end);
    }

    /**
     * 종목의 이력 OHLCV가 목표 일수(기본 200일)에 못 미치면 토스 캔들 조회를
     * 페이지네이션(count=200 + before/nextBefore)으로 반복 호출해 채운다.
     * 이미 충분하면 API를 호출하지 않고 즉시 반환한다.
     *
     * <p>외부 API 왕복과 딜레이가 여러 번 발생할 수 있어 전체를 하나의 트랜잭션으로
     * 묶지 않는다. 저장은 건별로 커밋된다.
     */
    public void backfillHistoryIfNeeded(String stockCode) {
        backfillHistoryIfNeeded(stockCode, BACKFILL_TARGET_DAYS);
    }

    public void backfillHistoryIfNeeded(String stockCode, int targetDays) {
        long existingCount = domesticDailyPriceRepository.countByStockCode(stockCode);
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
     * 덮어쓴다. {@link #backfillHistoryIfNeeded}와 페이지네이션 구조는
     * 같지만 "이미 충분하면 스킵"과 "존재하면 스킵"이 둘 다 없다 - 재조정은
     * 값이 있는 행 자체가 틀린 사건이라 스킵 조건이 성립하지 않는다.
     *
     * <p>목표를 {@link #BACKFILL_TARGET_DAYS}(200, 페이지 크기와 정확히
     * 동일)가 아니라 {@link #FULL_REBACKFILL_TARGET_DAYS}(400, 백테스트
     * 유니버스 심화 백필과 동일 관례)로 잡는다 - 200으로 뒀을 때
     * {@code rebackfillAdjustedHistoryUntil}의 루프 조건(`processed &lt;
     * targetDays`)이 정확히 페이지 크기(200)와 같아지는 경계에서, 실제로는
     * 더 받아올 이력이 있는데도(2페이지째 커서가 유효한데도) 1페이지만
     * 받고 종료해버리는 문제가 실제로 발생했다(2026-08-04 실제 복구 런북
     * 실행 중 발견 - 액면분할 시점이 약 210거래일 전이라 정확히 200일
     * 경계를 살짝 넘어서 있었음). 400으로 여유를 둬 이 경계 우연이 실제로
     * 문제가 되는 상황을 피한다.
     */
    public int rebackfillAdjustedHistory(String stockCode) {
        return rebackfillAdjustedHistoryUntil(stockCode, FULL_REBACKFILL_TARGET_DAYS);
    }

    /**
     * from이 주어지면 "달력일 갭 + 여유분"만큼의 캔들 개수만 요청해 페이지
     * 수를 최소화한다 - 복구 시나리오({@code DailyPriceIntegrityService})에서
     * 결정적으로 쓰인다.
     */
    public int rebackfillAdjustedHistory(String stockCode, LocalDate from) {
        long calendarGapDays = ChronoUnit.DAYS.between(from, LocalDate.now()) + REBACKFILL_BUFFER_DAYS;
        int targetDays = (int) Math.max(1, calendarGapDays);
        return rebackfillAdjustedHistoryUntil(stockCode, targetDays);
    }

    private int rebackfillAdjustedHistoryUntil(String stockCode, int targetDays) {
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
                UpsertOutcome outcome = upsertCandle(stockCode, tradeDate, candle, !overwriteAll);
                if (outcome.created()) {
                    saved++;
                }
                restatementDetected |= outcome.restatementDetected();
                continue;
            }
            if (domesticDailyPriceRepository.existsByStockCodeAndTradeDate(stockCode, tradeDate)) {
                continue;
            }
            try {
                domesticDailyPriceRepository.save(TossPriceMapper.toDailyPrice(stockCode, candle));
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
     * 트랜잭션 종료 시 자동 flush를 보장할 수 없다.
     *
     * @param detectRestatement false면 재조정 감지를 하지 않는다 -
     *     overwriteAll(재백필) 경로에서 무한 재귀를 막기 위함.
     */
    private UpsertOutcome upsertCandle(String stockCode, LocalDate tradeDate,
                                        TossCandleResponse.TossCandle candle, boolean detectRestatement) {
        return domesticDailyPriceRepository.findByStockCodeAndTradeDate(stockCode, tradeDate)
            .map(existing -> {
                long open = Long.parseLong(candle.openPrice());
                long high = Long.parseLong(candle.highPrice());
                long low = Long.parseLong(candle.lowPrice());
                long close = Long.parseLong(candle.closePrice());
                long volume = Long.parseLong(candle.volume());
                // 값이 동일하면 save() 자체를 생략한다 - 재확정 윈도우 도입으로
                // 종목당 MIN_LOOKBACK_CANDLES개씩 매 스윕 재조회되는데, 이미
                // 확정된 과거 거래일은 대부분 값이 그대로라 불필요한 UPDATE가
                // 매번 반복되는 걸 막는다.
                if (isUnchanged(existing, open, high, low, close, volume)) {
                    return UpsertOutcome.unchanged();
                }
                boolean restated = detectRestatement && DailyPriceSettlementPolicy.isRestatement(
                    existing.getClosePrice(), close, DailyPriceSettlementPolicy.DOMESTIC_RESTATEMENT_THRESHOLD);
                existing.updateOhlcv(open, high, low, close, volume);
                domesticDailyPriceRepository.save(existing);
                return UpsertOutcome.updated(restated);
            })
            .orElseGet(() -> {
                try {
                    domesticDailyPriceRepository.save(TossPriceMapper.toDailyPrice(stockCode, candle));
                    return UpsertOutcome.inserted();
                } catch (DataIntegrityViolationException e) {
                    log.debug("당일/재확정 시세 동시 저장 충돌 스킵: stockCode={}, date={}", stockCode, tradeDate);
                    return UpsertOutcome.unchanged();
                }
            });
    }

    private boolean isUnchanged(DomesticDailyPrice existing, long open, long high, long low, long close, long volume) {
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
