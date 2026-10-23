package com.quantlime.market.service;

import com.quantlime.price.implement.DailyPriceReader;
import com.quantlime.price.service.OverseasDailyPriceBackfillService;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
 *
 * <p>벤치마크 지수가 있는 시장(NASDAQ, NYSE)의 종목만 유니버스에 넣는다(2026-10-23) - 백테스트는 종목 수익률에서 시장 지수
 * 수익률을 뺀 초과수익률로 측정해서, 벤치마크가 없는 AMEX 종목은 {@code BacktestService}가 계산 전에 "지원하지 않는 시장"으로
 * 거절한다. 운영에서 해외 유니버스 398종목 중 AMEX가 242종목(61%)이라 주간 백테스트가 skip 상한을 넘어 실패했고, 그 종목들의
 * 백필 시간(해외 데이터셋 준비 약 6분 20초 중 절반 이상)도 헛돌았다.
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
    // BacktestService.BENCHMARK_INDEX_CODE에서 해외에 해당하는 시장(NASDAQ→NASDAQ, NYSE→SP500)과 같은 집합이다. market 패키지가
    // backtest 패키지를 참조하면 의존이 거꾸로(backtest → market이 정방향) 걸려서 여기에 복제한다 - 벤치마크 지수를 추가할 때
    // 세 곳(BacktestService, CrossSectionalBacktestService, 이 상수)을 같이 고칠 것.
    private static final Set<MarketType> BENCHMARK_MARKETS = Set.of(MarketType.NASDAQ, MarketType.NYSE);
    // 종목 간 호출 딜레이. 해외도 이제 국내와 같은 Toss 캔들 API를 쓰지만
    // (2026-07-29, KIS에서 이관), 최초 백필처럼 대부분 종목이 API를 실제로
    // 호출하는 상황에서는 여전히 레이트리밋 여유를 두는 게 안전하다.
    private static final long INTER_STOCK_DELAY_MS = 150;

    private final StockMasterService stockMasterService;
    private final OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;
    private final DailyPriceReader dailyPriceReader;
    private final Clock clock;

    public List<String> selectAndBackfillUniverse() {
        List<Stock> benchmarkMarketStocks = stockMasterService.getAllListedStocks().stream()
            .filter(stock -> BENCHMARK_MARKETS.contains(stock.getMarketType()))
            .toList();
        List<Stock> candidates = benchmarkMarketStocks.stream()
            .filter(stock -> !isRealEstateSector(stock))
            .toList();
        log.info("해외 유니버스 후보 종목(벤치마크 시장만, 부동산 섹터 제외): {}건", candidates.size());

        backfillEach(candidates, SCAN_TARGET_DAYS);

        LocalDate since = LocalDate.now(clock).minusMonths(3);
        // 거래대금 순위는 시장 구분이 없어서, 전체 순위에서 벤치마크 시장 종목만 남긴 뒤 상위 UNIVERSE_SIZE개를 자른다 -
        // 먼저 자르면 AMEX 같은 제외 대상이 슬롯을 차지해 유니버스가 줄어든다
        Set<String> benchmarkMarketCodes = benchmarkMarketStocks.stream()
            .map(Stock::getStockCode)
            .collect(Collectors.toSet());
        List<String> selectedCodes = dailyPriceReader.findOverseasCodesByTradingValueDesc(since).stream()
            .filter(benchmarkMarketCodes::contains)
            .limit(UNIVERSE_SIZE)
            .toList();
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
