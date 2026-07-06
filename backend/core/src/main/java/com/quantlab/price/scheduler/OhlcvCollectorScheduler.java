package com.quantlab.price.scheduler;

import com.quantlab.common.exception.ExternalApiException;
import com.quantlab.infra.toss.exception.TossApiErrorCode;
import com.quantlab.price.service.DailyPriceService;
import com.quantlab.score.service.ScoreService;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OhlcvCollectorScheduler {

    private static final long TOSS_API_DELAY_MS = 150;
    private static final long RATE_LIMIT_BACKOFF_MS = 3000;

    private final StockMasterService stockMasterService;
    private final DailyPriceService dailyPriceService;
    private final ScoreService scoreService;

    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Asia/Seoul")
    public void collectDailyOhlcv() {
        LocalDate today = LocalDate.now();
        List<Stock> stocks = stockMasterService.getAllListedStocks();
        log.info("OHLCV 수집 시작: date={}, 대상종목수={}",
            today, stocks.size());

        int successCount = 0;
        int failCount = 0;

        for (Stock stock : stocks) {
            try {
                collectWithRateLimitRetry(stock.getStockCode());
                successCount++;
                Thread.sleep(TOSS_API_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("OHLCV 수집 중단: 인터럽트 발생");
                break;
            } catch (Exception e) {
                failCount++;
                log.error("OHLCV 수집 실패: stockCode={}, error={}",
                    stock.getStockCode(), e.getMessage(), e);
            }
        }

        log.info("OHLCV 수집 완료: 성공={}, 실패={}",
            successCount, failCount);

        recalculateScoresSafely();
    }

    private void recalculateScoresSafely() {
        try {
            scoreService.recalculateWatchlistedScores();
        } catch (Exception e) {
            // 스코어 재계산 실패(퀀트 엔진 장애 등)가 이번 배치 전체를 실패로
            // 만들지 않도록 로그만 남긴다. 조회 API는 직전 이력을 그대로
            // 반환한다(fallback).
            log.error("스코어 일괄 재계산 실패: error={}", e.getMessage(), e);
        }
    }

    private void collectWithRateLimitRetry(String stockCode) throws InterruptedException {
        try {
            dailyPriceService.collectDailyPrice(stockCode);
        } catch (ExternalApiException e) {
            if (!TossApiErrorCode.RATE_LIMIT_EXCEEDED.getCode().equals(e.getCode())) {
                throw e;
            }
            log.warn("Rate Limit 도달, {}ms 대기 후 1회 재시도: stockCode={}",
                RATE_LIMIT_BACKOFF_MS, stockCode);
            Thread.sleep(RATE_LIMIT_BACKOFF_MS);
            dailyPriceService.collectDailyPrice(stockCode);
        }
    }
}
