package com.quantlime.price.service;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.dto.PriceJumpReport;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.util.DailyPriceSettlementPolicy;
import com.quantlime.price.util.PriceJumpDetector;
import com.quantlime.price.util.PriceJumpDetector.PricePoint;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수정주가 소급 재조정(액면분할/병합) 버그 복구 전용(일회성 수동 트리거).
 * 신규 오염은 {@link DomesticDailyPriceService}의 저장 경로가 재확정
 * 윈도우 안에서 자동으로 감지·복구하지만, 이미 그 윈도우 밖에 박혀 있던
 * 기존 오염(2025-06-01 이후 전 구간)은 일회성 스캔+표적 재백필이 필요하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPriceIntegrityService {

    private static final int SCAN_CHUNK_SIZE = 100;
    // DailyPriceResettlementService.INTER_STOCK_DELAY_MS와 동일한 값.
    private static final long INTER_STOCK_DELAY_MS = 150;

    private final StockMasterService stockMasterService;
    private final DomesticDailyPriceRepository domesticDailyPriceRepository;
    private final DomesticDailyPriceService domesticDailyPriceService;

    /**
     * API 호출 없이 DB에 이미 저장된 값만으로 이상 점프를 스캔한다 - lag
     * 자기조인을 리포지토리에 native 쿼리로 밀어넣지 않고, 종목별 시계열을
     * 메모리에서 정렬한 뒤 순수 함수({@link PriceJumpDetector})에 넘긴다.
     */
    @Transactional(readOnly = true)
    public List<PriceJumpReport> scanDomesticJumps(LocalDate from) {
        List<String> stockCodes = stockMasterService.getAllListedStocks().stream()
            .filter(stock -> stock.getMarketType().isDomestic())
            .map(Stock::getStockCode)
            .toList();

        List<PriceJumpReport> jumps = new ArrayList<>();
        for (List<String> chunk : partition(stockCodes, SCAN_CHUNK_SIZE)) {
            List<DomesticDailyPrice> prices = domesticDailyPriceRepository
                .findByStockCodeInAndTradeDateBetweenOrderByTradeDateDesc(chunk, from, LocalDate.now());
            Map<String, List<DomesticDailyPrice>> byStockCode = prices.stream()
                .collect(Collectors.groupingBy(DomesticDailyPrice::getStockCode));
            for (Map.Entry<String, List<DomesticDailyPrice>> entry : byStockCode.entrySet()) {
                List<PricePoint> points = entry.getValue().stream()
                    .sorted(Comparator.comparing(DomesticDailyPrice::getTradeDate))
                    .map(price -> new PricePoint(price.getTradeDate(), price.getClosePrice()))
                    .toList();
                jumps.addAll(PriceJumpDetector.detect(
                    entry.getKey(), points, DailyPriceSettlementPolicy.DOMESTIC_RESTATEMENT_THRESHOLD));
            }
        }
        log.info("수정주가 재조정 스캔 완료: 대상종목수={}, 발견={}건", stockCodes.size(), jumps.size());
        return jumps;
    }

    /**
     * 스캔에서 걸린 종목만 전 구간 재백필한다. dryRun=true면 스캔 결과만
     * 반환하고 실제 재백필(외부 API 호출)은 하지 않는다 - 복구 런북 1단계
     * (영향 범위를 눈으로 먼저 확인)용.
     */
    public List<PriceJumpReport> repairDomesticAdjusted(LocalDate from, boolean dryRun) {
        List<PriceJumpReport> jumps = scanDomesticJumps(from);
        if (dryRun) {
            return jumps;
        }

        List<String> affectedStockCodes = jumps.stream()
            .map(PriceJumpReport::stockCode)
            .distinct()
            .toList();
        log.info("수정주가 재조정 복구 시작: 대상종목수={}", affectedStockCodes.size());
        for (String stockCode : affectedStockCodes) {
            try {
                domesticDailyPriceService.rebackfillAdjustedHistory(stockCode);
            } catch (Exception e) {
                log.error("수정주가 재조정 복구 실패(해당 종목만 스킵): stockCode={}, error={}",
                    stockCode, e.getMessage(), e);
            }
            if (!sleepBetweenStocks()) {
                break;
            }
        }
        log.info("수정주가 재조정 복구 완료: 대상종목수={}", affectedStockCodes.size());
        return jumps;
    }

    private boolean sleepBetweenStocks() {
        try {
            Thread.sleep(INTER_STOCK_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("수정주가 재조정 복구 중단: 인터럽트 발생");
            return false;
        }
    }

    private static List<List<String>> partition(List<String> list, int size) {
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }
}
