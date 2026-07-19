package com.quantlab.market.service;

import com.quantlab.price.dto.OverseasStockTradingValue;
import com.quantlab.price.repository.OverseasDailyPriceRepository;
import com.quantlab.price.service.OverseasDailyPriceBackfillService;
import com.quantlab.stock.domain.MarketType;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 백테스트 해외 유니버스(거래대금 상위 500)를 2-pass로 선정한다 -
 * {@link DomesticUniverseSelectionService}의 해외판. ETF/REIT 제외는
 * 종목마스터 동기화 단계(OverseasStockMasterSyncService)에서 이미
 * 종목구분(2:주식)으로 걸러냈으므로 여기서는 별도 필터가 필요 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasUniverseSelectionService {

    private static final Map<MarketType, String> EXCHANGE_CODE = Map.of(
        MarketType.NASDAQ, "NAS",
        MarketType.NYSE, "NYS"
    );
    private static final int SCAN_TARGET_DAYS = 60;
    private static final int UNIVERSE_TARGET_DAYS = 400;
    private static final int UNIVERSE_SIZE = 500;

    private final StockMasterService stockMasterService;
    private final OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;

    public List<String> selectAndBackfillUniverse() {
        List<Stock> candidates = stockMasterService.getAllListedStocks().stream()
            .filter(stock -> EXCHANGE_CODE.containsKey(stock.getMarketType()))
            .toList();
        log.info("해외 유니버스 후보 종목: {}건", candidates.size());

        backfillEach(candidates, SCAN_TARGET_DAYS);

        LocalDate since = LocalDate.now().minusMonths(3);
        List<OverseasStockTradingValue> ranked =
            overseasDailyPriceRepository.findTopByTradingValue(since, UNIVERSE_SIZE);
        List<String> selectedCodes = ranked.stream().map(OverseasStockTradingValue::stockCode).toList();
        log.info("해외 거래대금 상위 {}종목 선정 완료(기준일 {} 이후)", selectedCodes.size(), since);

        List<Stock> selected = candidates.stream()
            .filter(stock -> selectedCodes.contains(stock.getStockCode()))
            .toList();
        backfillEach(selected, UNIVERSE_TARGET_DAYS);
        log.info("해외 유니버스 선정+백필 완료: {}종목, 목표={}일", selected.size(), UNIVERSE_TARGET_DAYS);
        return selectedCodes;
    }

    private void backfillEach(List<Stock> stocks, int targetDays) {
        for (Stock stock : stocks) {
            String exchangeCode = EXCHANGE_CODE.get(stock.getMarketType());
            try {
                overseasDailyPriceBackfillService.backfillHistoryIfNeeded(
                    stock.getStockCode(), exchangeCode, targetDays);
            } catch (Exception e) {
                log.error("해외 유니버스 백필 실패: stockCode={}, exchangeCode={}, targetDays={}, error={}",
                    stock.getStockCode(), exchangeCode, targetDays, e.getMessage(), e);
            }
        }
    }
}
