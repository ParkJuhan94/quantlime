package com.quantlime.market.service;

import com.quantlime.price.dto.StockTradingValue;
import com.quantlime.price.repository.DailyPriceRepository;
import com.quantlime.price.service.DailyPriceService;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 백테스트 국내 유니버스(거래대금 상위 500, ETF/REIT 제외)를 2-pass로
 * 선정한다 - CLAUDE.md 스코어링 백테스트 계획 Phase C 참고.
 *
 * <p>ETF는 별도 필터가 필요 없다: 종목마스터(KIND 상장법인목록)가 원래
 * "법인"만 다루고 ETF/ETN은 집합투자기구라 그 목록 자체에 없음을 DB로
 * 실측 확인했다(KODEX/TIGER 등 프리픽스 매칭 0건). REIT는 실제 상장법인
 * (부동산투자회사법인)이라 존재하므로 이름 기반으로 걸러낸다 - "리츠"가
 * 포함되지만 REIT가 아닌 종목 2건(메리츠금융지주, 블리츠웨이엔터테인먼트)은
 * 예외 처리. 반대로 "이리츠코크렙"처럼 접미사가 "리츠"로 끝나지 않는
 * 진짜 REIT도 있어 접미사 매칭 대신 포함 매칭 + 예외 목록을 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DomesticUniverseSelectionService {

    private static final Set<String> REIT_NAME_FALSE_POSITIVES =
        Set.of("메리츠금융지주", "블리츠웨이엔터테인먼트");
    // 1차 스캔(거래대금 랭킹용) 목표 일수 - "최근 3개월" 거래대금 합산 기준과
    // 거의 일치(거래일 기준 3개월 ≈ 60거래일).
    private static final int SCAN_TARGET_DAYS = 60;
    private static final int UNIVERSE_TARGET_DAYS = 400;
    private static final int UNIVERSE_SIZE = 500;

    private final StockMasterService stockMasterService;
    private final DailyPriceService dailyPriceService;
    private final DailyPriceRepository dailyPriceRepository;

    /**
     * 1차: REIT 제외 전 상장종목의 최근 60거래일을 백필한다(이미 충분하면
     * API 호출 없이 스킵). 2차: 최근 3개월 누적 거래대금 상위 500종목만
     * 400거래일로 깊게 백필한다.
     */
    public List<String> selectAndBackfillUniverse() {
        List<Stock> candidates = stockMasterService.getAllListedStocks().stream()
            .filter(stock -> !isReit(stock.getStockName()))
            .toList();
        log.info("유니버스 후보 종목(REIT 제외): {}건", candidates.size());

        backfillEach(candidates.stream().map(Stock::getStockCode).toList(), SCAN_TARGET_DAYS);

        LocalDate since = LocalDate.now().minusMonths(3);
        List<StockTradingValue> ranked = dailyPriceRepository.findTopByTradingValue(since, UNIVERSE_SIZE);
        List<String> selected = ranked.stream().map(StockTradingValue::stockCode).toList();
        log.info("거래대금 상위 {}종목 선정 완료(기준일 {} 이후)", selected.size(), since);

        backfillEach(selected, UNIVERSE_TARGET_DAYS);
        log.info("국내 유니버스 선정+백필 완료: {}종목, 목표={}일", selected.size(), UNIVERSE_TARGET_DAYS);
        return selected;
    }

    private void backfillEach(List<String> stockCodes, int targetDays) {
        for (String stockCode : stockCodes) {
            try {
                dailyPriceService.backfillHistoryIfNeeded(stockCode, targetDays);
            } catch (Exception e) {
                log.error("유니버스 백필 실패: stockCode={}, targetDays={}, error={}",
                    stockCode, targetDays, e.getMessage(), e);
            }
        }
    }

    private boolean isReit(String stockName) {
        return stockName.contains("리츠") && !REIT_NAME_FALSE_POSITIVES.contains(stockName);
    }
}
