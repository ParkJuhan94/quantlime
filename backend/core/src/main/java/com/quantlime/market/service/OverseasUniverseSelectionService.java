package com.quantlime.market.service;

import com.quantlime.price.dto.OverseasStockTradingValue;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.service.OverseasDailyPriceBackfillService;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 백테스트 해외 유니버스(거래대금 상위 500)를 2-pass로 선정한다 -
 * {@link DomesticUniverseSelectionService}의 해외판. ETF 제외는 종목마스터
 * 동기화 단계(OverseasStockMasterSyncService)에서 이미 종목구분(2:주식)으로
 * 걸러냈지만, REIT는 미국 시장에서 ETF가 아니라 보통주로 거래돼(예: Realty
 * Income, AMT, PLD) 그 필터를 그대로 통과한다 - 국내 유니버스가 이름 매칭으로
 * REIT를 걸러내는 것과 달리, 여기서는 KIS 마스터파일 업종코드(Stock.sector에
 * 저장됨, KisOverseasStockMasterEntry.INDUSTRY_CODE_REAL_ESTATE 참고)로
 * "630"(부동산 섹터, REIT+CBRE 등 부동산 서비스업 포함)을 걸러낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasUniverseSelectionService {

    // KisOverseasStockMasterEntry.INDUSTRY_CODE_REAL_ESTATE와 동일한 값 -
    // Stock.sector에 저장되는 시점(OverseasStockMasterSyncService)에는 record
    // 상수를 그대로 재사용했지만, 여기서는 Stock 엔티티의 문자열 필드와
    // 비교해야 해 별도 상수로 둔다(같은 값을 두 곳에 두는 것 자체보다,
    // enum이 아닌 자유 문자열 필드에 의존하는 것 자체가 이 방식의 한계).
    private static final String INDUSTRY_CODE_REAL_ESTATE = "630";
    private static final int SCAN_TARGET_DAYS = 60;
    // DomesticUniverseSelectionService.UNIVERSE_TARGET_DAYS와 동일한 이유
    // (백테스트 워밍업 제외 후에도 horizon=60 표본 확보).
    private static final int UNIVERSE_TARGET_DAYS = 520;
    private static final int UNIVERSE_SIZE = 500;
    // 종목 간 호출 딜레이. 해외도 이제 국내와 같은 Toss 캔들 API를 쓰지만
    // (2026-07-29, KIS에서 이관), 최초 백필처럼 대부분 종목이 API를 실제로
    // 호출하는 상황에서는 여전히 레이트리밋 여유를 두는 게 안전하다.
    private static final long INTER_STOCK_DELAY_MS = 150;

    private final StockMasterService stockMasterService;
    private final OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;

    public List<String> selectAndBackfillUniverse() {
        List<Stock> candidates = stockMasterService.getAllListedStocks().stream()
            .filter(stock -> !stock.getMarketType().isDomestic())
            .filter(stock -> !isRealEstateSector(stock))
            .toList();
        log.info("해외 유니버스 후보 종목(부동산 섹터 제외): {}건", candidates.size());

        backfillEach(candidates, SCAN_TARGET_DAYS);

        LocalDate since = LocalDate.now().minusMonths(3);
        List<OverseasStockTradingValue> ranked =
            overseasDailyPriceRepository.findTopByTradingValue(since, UNIVERSE_SIZE);
        List<String> selectedCodes = ranked.stream().map(OverseasStockTradingValue::stockCode).toList();
        log.info("해외 거래대금 상위 {}종목 선정 완료(기준일 {} 이후)", selectedCodes.size(), since);

        Set<String> selectedCodeSet = Set.copyOf(selectedCodes);
        List<Stock> selected = candidates.stream()
            .filter(stock -> selectedCodeSet.contains(stock.getStockCode()))
            .toList();
        backfillEach(selected, UNIVERSE_TARGET_DAYS);
        log.info("해외 유니버스 선정+백필 완료: {}종목, 목표={}일", selected.size(), UNIVERSE_TARGET_DAYS);
        return selectedCodes;
    }

    private boolean isRealEstateSector(Stock stock) {
        return INDUSTRY_CODE_REAL_ESTATE.equals(stock.getSector());
    }

    private void backfillEach(List<Stock> stocks, int targetDays) {
        for (Stock stock : stocks) {
            try {
                overseasDailyPriceBackfillService.backfillHistoryIfNeeded(stock.getStockCode(), targetDays);
            } catch (Exception e) {
                log.error("해외 유니버스 백필 실패: stockCode={}, targetDays={}, error={}",
                    stock.getStockCode(), targetDays, e.getMessage(), e);
            }
            if (!sleepBetweenStocks()) {
                return;
            }
        }
    }

    private boolean sleepBetweenStocks() {
        try {
            Thread.sleep(INTER_STOCK_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("해외 유니버스 백필 중단: 인터럽트 발생");
            return false;
        }
    }
}
