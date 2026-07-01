package com.quantlab.price.scheduler;

import com.quantlab.price.service.DailyPriceService;
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

    private static final long KIS_API_DELAY_MS = 60;

    private final StockMasterService stockMasterService;
    private final DailyPriceService dailyPriceService;

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
                dailyPriceService.collectDailyPrice(
                    stock.getStockCode(), today);
                successCount++;
                Thread.sleep(KIS_API_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("OHLCV 수집 중단: 인터럽트 발생");
                break;
            } catch (Exception e) {
                failCount++;
                log.error("OHLCV 수집 실패: stockCode={}, error={}",
                    stock.getStockCode(), e.getMessage());
            }
        }

        log.info("OHLCV 수집 완료: 성공={}, 실패={}",
            successCount, failCount);
    }
}
