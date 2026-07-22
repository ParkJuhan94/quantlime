package com.quantlime.price.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.kis.KisApiClient;
import com.quantlime.infra.kis.dto.KisOverseasDailyPriceResponse;
import com.quantlime.infra.kis.exception.KisApiErrorCode;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 해외주식 일별 OHLCV 백필. 구조는 {@link DailyPriceService}의 토스 백필과
 * 동일(전체를 하나의 트랜잭션으로 묶지 않고 건별 저장)하되, 페이지네이션
 * 방식이 다르다 - 토스는 count/before 커서인 반면 KIS는 BYMD(기준일자)를
 * 이전 페이지 최고령일의 하루 전으로 갱신하는 방식이다(실제 호출로
 * BYMD=20260713 조회 시 그 날짜부터 과거 100건이 내려옴을 확인).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasDailyPriceBackfillService {

    // KIS 해외주식 기간별시세는 호출당 최대 100건을 준다(실제 응답의
    // output1.nrec로 확인).
    private static final int BACKFILL_PAGE_SIZE = 100;
    private static final long API_DELAY_MS = 150;
    private static final long RATE_LIMIT_BACKOFF_MS = 3000;
    private static final DateTimeFormatter BASE_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final OverseasDailyPriceRepository overseasDailyPriceRepository;
    private final KisApiClient kisApiClient;

    public void backfillHistoryIfNeeded(String stockCode, String exchangeCode, int targetDays) {
        long existingCount = overseasDailyPriceRepository.countByStockCode(stockCode);
        if (existingCount >= targetDays) {
            log.debug("해외 이력 백필 불필요: stockCode={}, 기존건수={}", stockCode, existingCount);
            return;
        }

        String baseDate = null;
        int savedCount = 0;

        while (savedCount < targetDays) {
            KisOverseasDailyPriceResponse response =
                fetchWithRetry(exchangeCode, stockCode, baseDate);
            List<KisOverseasDailyPriceResponse.Candle> candles = response.output2();
            if (candles == null || candles.isEmpty()) {
                break;
            }

            savedCount += saveNewCandles(stockCode, candles);

            boolean noMoreHistory = candles.size() < BACKFILL_PAGE_SIZE;
            if (noMoreHistory) {
                break;
            }
            baseDate = previousDay(candles.get(candles.size() - 1).xymd());

            if (!sleepBeforeNextPage(stockCode)) {
                return;
            }
        }
    }

    private int saveNewCandles(String stockCode, List<KisOverseasDailyPriceResponse.Candle> candles) {
        int saved = 0;
        for (KisOverseasDailyPriceResponse.Candle candle : candles) {
            LocalDate tradeDate = LocalDate.parse(candle.xymd(), BASE_DATE_FORMAT);
            if (overseasDailyPriceRepository.existsByStockCodeAndTradeDate(stockCode, tradeDate)) {
                continue;
            }
            try {
                overseasDailyPriceRepository.save(OverseasDailyPrice.of(
                    stockCode,
                    tradeDate,
                    Double.parseDouble(candle.open()),
                    Double.parseDouble(candle.high()),
                    Double.parseDouble(candle.low()),
                    Double.parseDouble(candle.clos()),
                    Long.parseLong(candle.tvol())));
                saved++;
            } catch (DataIntegrityViolationException e) {
                log.debug("해외 이력 백필 중복 저장 스킵: stockCode={}, date={}", stockCode, tradeDate);
            }
        }
        return saved;
    }

    private String previousDay(String yyyymmdd) {
        return LocalDate.parse(yyyymmdd, BASE_DATE_FORMAT).minusDays(1).format(BASE_DATE_FORMAT);
    }

    private boolean sleepBeforeNextPage(String stockCode) {
        try {
            Thread.sleep(API_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("해외 이력 백필 중단: 인터럽트 발생, stockCode={}", stockCode);
            return false;
        }
    }

    private KisOverseasDailyPriceResponse fetchWithRetry(
        String exchangeCode, String stockCode, String baseDate) {
        try {
            return kisApiClient.getOverseasDailyPrice(exchangeCode, stockCode, baseDate);
        } catch (ExternalApiException e) {
            if (!KisApiErrorCode.RATE_LIMIT_EXCEEDED.getCode().equals(e.getCode())) {
                throw e;
            }
            log.warn("Rate Limit 도달, {}ms 대기 후 1회 재시도: stockCode={}",
                RATE_LIMIT_BACKOFF_MS, stockCode);
            try {
                Thread.sleep(RATE_LIMIT_BACKOFF_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ExternalApiException(KisApiErrorCode.RATE_LIMIT_EXCEEDED, interrupted);
            }
            return kisApiClient.getOverseasDailyPrice(exchangeCode, stockCode, baseDate);
        }
    }
}
