package com.quantlime.price.service;

import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 장중 스냅샷 영구 고정 버그 복구 전용(일회성 수동 트리거). 재확정 윈도우
 * ({@link com.quantlime.price.util.DailyPriceSettlementPolicy#RESETTLEMENT_WINDOW_DAYS})
 * 도입 이후의 신규 오염은 정기 스윕이 자동으로 막지만, 이미 그 윈도우
 * 밖으로 밀려난 기존 오염은 자동 복구가 안 된다 - 전 상장종목에 대해
 * from~오늘 구간을 무조건 재조회해 덮어쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPriceResettlementService {

    // MarketDataRefreshService.INTER_STOCK_DELAY_MS와 동일한 값 - 전종목
    // 순회 시 레이트리밋을 넘지 않기 위한 종목 간 딜레이.
    private static final long INTER_STOCK_DELAY_MS = 150;

    private final StockMasterService stockMasterService;
    private final DomesticDailyPriceService domesticDailyPriceService;
    private final OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;

    public int resettleDomestic(LocalDate from) {
        return resettle(listedStockCodes(true), code -> domesticDailyPriceService.rebackfillAdjustedHistory(code, from));
    }

    public int resettleOverseas(LocalDate from) {
        return resettle(listedStockCodes(false),
            code -> overseasDailyPriceBackfillService.rebackfillAdjustedHistory(code, from));
    }

    private List<String> listedStockCodes(boolean domestic) {
        return stockMasterService.getAllListedStocks().stream()
            .filter(stock -> stock.getMarketType().isDomestic() == domestic)
            .filter(stock -> !stock.isPriceUnsupported())
            .map(Stock::getStockCode)
            .toList();
    }

    private int resettle(List<String> stockCodes, ToIntFunction<String> rebackfill) {
        log.info("가격 재확정 일괄 복구 시작: 대상종목수={}", stockCodes.size());
        int processed = 0;
        int failed = 0;
        for (String stockCode : stockCodes) {
            try {
                rebackfill.applyAsInt(stockCode);
                processed++;
            } catch (Exception e) {
                failed++;
                log.error("가격 재확정 실패(해당 종목만 스킵): stockCode={}, error={}",
                    stockCode, e.getMessage(), e);
            }
            if (!sleepBetweenStocks()) {
                break;
            }
        }
        log.info("가격 재확정 일괄 복구 완료: 처리={}, 실패={}", processed, failed);
        return processed;
    }

    private boolean sleepBetweenStocks() {
        try {
            Thread.sleep(INTER_STOCK_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("가격 재확정 복구 중단: 인터럽트 발생");
            return false;
        }
    }
}
