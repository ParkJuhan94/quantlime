package com.quantlime.price.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.infra.toss.dto.TossPriceMapper;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.repository.DailyPriceRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPriceService {

    private static final int BACKFILL_TARGET_DAYS = 200;
    private static final int BACKFILL_PAGE_SIZE = 200;
    private static final long BACKFILL_API_DELAY_MS = 150;
    private static final long BACKFILL_RATE_LIMIT_BACKOFF_MS = 3000;
    // 배치가 하루 이상 못 돈 날(로컬 개발 서버 다운타임, 공휴일 스케줄 밀림
    // 등)에도 다음 실행에서 자동으로 따라잡을 수 있도록 "최신 1건"이 아니라
    // 최근 며칠치를 함께 조회한다.
    private static final int DAILY_COLLECT_LOOKBACK_DAYS = 10;

    private final DailyPriceRepository dailyPriceRepository;
    private final TossApiClient tossApiClient;

    @Transactional
    public void collectDailyPrice(String stockCode) {
        TossCandleResponse response = tossApiClient.getDailyCandles(
            stockCode, DAILY_COLLECT_LOOKBACK_DAYS, null);

        List<TossCandleResponse.TossCandle> candles = response.result().candles();
        if (candles == null || candles.isEmpty()) {
            log.warn("시세 데이터 없음: stockCode={}", stockCode);
            return;
        }

        int savedCount = saveNewCandles(stockCode, candles);
        if (savedCount > 0) {
            log.info("일별 시세 수집 완료: stockCode={}, 신규저장={}건", stockCode, savedCount);
        } else {
            log.debug("이미 수집된 데이터: stockCode={}", stockCode);
        }
    }

    @Transactional(readOnly = true)
    public List<DailyPrice> getDailyPrices(String stockCode, LocalDate start, LocalDate end) {
        return dailyPriceRepository
            .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, start, end);
    }

    /**
     * 여러 종목의 이력을 한 번의 쿼리로 조회한다. 종목 수만큼 순차 조회하는
     * N+1 패턴을 피하기 위한 배치 버전 - 호출 측에서 stockCode별로 그룹핑해
     * 사용한다.
     */
    @Transactional(readOnly = true)
    public List<DailyPrice> getDailyPrices(List<String> stockCodes, LocalDate start, LocalDate end) {
        if (stockCodes.isEmpty()) {
            return List.of();
        }
        return dailyPriceRepository
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
        long existingCount = dailyPriceRepository.countByStockCode(stockCode);
        if (existingCount >= targetDays) {
            log.debug("이력 백필 불필요: stockCode={}, 기존건수={}", stockCode, existingCount);
            return;
        }

        log.info("이력 백필 시작: stockCode={}, 목표={}일, 기존={}건",
            stockCode, targetDays, existingCount);

        String cursor = null;
        int savedCount = 0;

        while (savedCount < targetDays) {
            TossCandleResponse response = fetchCandlesWithRetry(stockCode, BACKFILL_PAGE_SIZE, cursor);
            List<TossCandleResponse.TossCandle> candles = response.result().candles();
            if (candles == null || candles.isEmpty()) {
                break;
            }

            savedCount += saveNewCandles(stockCode, candles);

            boolean noMoreHistory = candles.size() < BACKFILL_PAGE_SIZE
                || response.result().nextBefore() == null;
            if (noMoreHistory) {
                break;
            }
            cursor = response.result().nextBefore();

            if (!sleepBeforeNextPage(stockCode)) {
                return;
            }
        }

        log.info("이력 백필 완료: stockCode={}, 신규저장={}건", stockCode, savedCount);
    }

    /**
     * 과거 거래일 행은 이미 확정된 값이라 존재하면 그대로 스킵한다. 다만
     * 당일(오늘) 행은 예외다 - 관심종목 등록 시 백필(backfillHistoryIfNeeded)이
     * 장중에 먼저 실행되면 아직 확정되지 않은 당일 캔들이 미리 저장되는데,
     * 이후 장 마감(15:30) 배치가 같은 날짜에 이미 행이 있다는 이유로 조용히
     * 스킵해버리면 확정 종가가 영영 반영되지 않는다(다음날 전일종가 계산이
     * 틀어지는 버그의 원인이었음) - 당일 행만 항상 최신값으로 덮어쓴다.
     */
    private int saveNewCandles(String stockCode, List<TossCandleResponse.TossCandle> candles) {
        LocalDate today = LocalDate.now();
        int saved = 0;
        for (TossCandleResponse.TossCandle candle : candles) {
            LocalDate tradeDate = TossPriceMapper.toLocalDate(candle.timestamp());
            if (tradeDate.isEqual(today)) {
                if (upsertToday(stockCode, tradeDate, candle)) {
                    saved++;
                }
                continue;
            }
            if (dailyPriceRepository.existsByStockCodeAndTradeDate(stockCode, tradeDate)) {
                continue;
            }
            try {
                dailyPriceRepository.save(TossPriceMapper.toDailyPrice(stockCode, candle));
                saved++;
            } catch (DataIntegrityViolationException e) {
                log.debug("이력 백필 중복 저장 스킵: stockCode={}, date={}", stockCode, tradeDate);
            }
        }
        return saved;
    }

    /**
     * 갱신 분기는 dirty checking에 기대지 않고 명시적으로 save()한다 -
     * backfillHistoryIfNeeded 경로는(위 클래스 주석 참고) 메서드 자체가
     * @Transactional이 아니라 건별로 커밋되므로, findBy로 읽어온 엔티티가
     * 이미 detached 상태일 수 있어 트랜잭션 종료 시 자동 flush를 보장할 수
     * 없다.
     */
    private boolean upsertToday(String stockCode, LocalDate tradeDate,
                                TossCandleResponse.TossCandle candle) {
        return dailyPriceRepository.findByStockCodeAndTradeDate(stockCode, tradeDate)
            .map(existing -> {
                existing.updateOhlcv(
                    Long.parseLong(candle.openPrice()),
                    Long.parseLong(candle.highPrice()),
                    Long.parseLong(candle.lowPrice()),
                    Long.parseLong(candle.closePrice()),
                    Long.parseLong(candle.volume()));
                dailyPriceRepository.save(existing);
                return false;
            })
            .orElseGet(() -> {
                try {
                    dailyPriceRepository.save(TossPriceMapper.toDailyPrice(stockCode, candle));
                    return true;
                } catch (DataIntegrityViolationException e) {
                    log.debug("당일 시세 동시 저장 충돌 스킵: stockCode={}, date={}", stockCode, tradeDate);
                    return false;
                }
            });
    }

    private boolean sleepBeforeNextPage(String stockCode) {
        try {
            Thread.sleep(BACKFILL_API_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("이력 백필 중단: 인터럽트 발생, stockCode={}", stockCode);
            return false;
        }
    }

    private TossCandleResponse fetchCandlesWithRetry(String stockCode, int count, String before) {
        try {
            return tossApiClient.getDailyCandles(stockCode, count, before);
        } catch (ExternalApiException e) {
            if (!TossApiErrorCode.RATE_LIMIT_EXCEEDED.getCode().equals(e.getCode())) {
                throw e;
            }
            log.warn("Rate Limit 도달, {}ms 대기 후 1회 재시도: stockCode={}",
                BACKFILL_RATE_LIMIT_BACKOFF_MS, stockCode);
            try {
                Thread.sleep(BACKFILL_RATE_LIMIT_BACKOFF_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ExternalApiException(TossApiErrorCode.RATE_LIMIT_EXCEEDED, interrupted);
            }
            return tossApiClient.getDailyCandles(stockCode, count, before);
        }
    }
}
