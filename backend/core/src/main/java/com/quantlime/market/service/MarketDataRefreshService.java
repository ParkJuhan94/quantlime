package com.quantlime.market.service;

import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.DailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.service.PriceGapFillService;
import com.quantlime.score.repository.ScoreRepository;
import com.quantlime.score.service.ScoreService;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 트리거1(운영 갱신) - 전체 상장종목(국내+해외)의 가격+스코어를 "마지막
 * 저장일 다음날부터 오늘까지"만 gap-fill한다({@link PriceGapFillService} 참고).
 * 국내/해외는 서로 다른 외부 API(Toss/KIS)라 레이트리밋이 독립적이므로 별도
 * 스레드에서 병렬 실행한다.
 *
 * <p>이 서비스 하나가 dev 수동 트리거(/dev/refresh), 매일 16:00 배치
 * (OhlcvCollectorScheduler), 로컬 백엔드 기동 시 자동 캐치업
 * (MarketDataStartupRunner) 3곳에서 재사용된다 - 기존에는 이 셋이 각자
 * "고정 10일 재조회", "총 건수 기준 백필", "앵커종목 하나만 보고 판단"으로
 * 서로 다른 방식이었다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataRefreshService {

    private static final Map<MarketType, String> OVERSEAS_EXCHANGE_CODE = Map.of(
        MarketType.NASDAQ, "NAS",
        MarketType.NYSE, "NYS"
    );
    // 종목 간 API 호출 딜레이(Toss/KIS 공통, 기존 OhlcvCollectorScheduler/
    // OverseasUniverseSelectionService와 동일한 값) - 실제로 외부 API를
    // 호출한 종목 다음에만 걸어, 이미 최신이라 스킵된 종목까지 매번 딜레이를
    // 물지 않는다(그래야 정상 상태에서 전종목 스윕이 빠르게 끝난다).
    private static final long INTER_STOCK_DELAY_MS = 150;

    private final StockMasterService stockMasterService;
    private final DailyPriceRepository dailyPriceRepository;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;
    private final ScoreRepository scoreRepository;
    private final PriceGapFillService priceGapFillService;
    private final ScoreService scoreService;
    private final TaskExecutor domesticMarketDataRefreshTaskExecutor;
    private final TaskExecutor overseasMarketDataRefreshTaskExecutor;

    public void refreshAll() {
        List<Stock> stocks = stockMasterService.getAllListedStocks();
        List<Stock> domestic = stocks.stream()
            .filter(stock -> stock.getMarketType().isDomestic())
            .toList();
        List<Stock> overseas = stocks.stream()
            .filter(stock -> !stock.getMarketType().isDomestic())
            .toList();

        CompletableFuture<Void> domesticDone = CompletableFuture.runAsync(
            () -> refreshDomestic(domestic), domesticMarketDataRefreshTaskExecutor);
        CompletableFuture<Void> overseasDone = CompletableFuture.runAsync(
            () -> refreshOverseas(overseas), overseasMarketDataRefreshTaskExecutor);
        CompletableFuture.allOf(domesticDone, overseasDone).join();

        log.info("전종목 가격+스코어 갱신 완료: 국내={}종목, 해외={}종목", domestic.size(), overseas.size());
    }

    public void refreshStock(String stockCode) {
        Stock stock = stockMasterService.getStockByCode(stockCode);
        if (stock.getMarketType().isDomestic()) {
            refreshDomestic(List.of(stock));
        } else {
            refreshOverseas(List.of(stock));
        }
    }

    private void refreshDomestic(List<Stock> stocks) {
        List<String> needsScoreRefresh = new ArrayList<>();
        for (Stock stock : stocks) {
            String stockCode = stock.getStockCode();
            try {
                boolean calledApi = priceGapFillService.fillDomesticGap(stockCode);
                if (calledApi && !sleepBetweenStocks()) {
                    break;
                }
            } catch (Exception e) {
                log.error("국내 가격 갱신 실패(해당 종목만 스킵): stockCode={}, error={}",
                    stockCode, e.getMessage(), e);
            }
            if (needsScoreRefresh(stockCode, latestPriceDate(stockCode))) {
                needsScoreRefresh.add(stockCode);
            }
        }
        scoreService.recalculateScores(needsScoreRefresh);
    }

    private void refreshOverseas(List<Stock> stocks) {
        List<String> needsScoreRefresh = new ArrayList<>();
        for (Stock stock : stocks) {
            String stockCode = stock.getStockCode();
            String exchangeCode = OVERSEAS_EXCHANGE_CODE.get(stock.getMarketType());
            if (exchangeCode == null) {
                continue;
            }
            try {
                boolean calledApi = priceGapFillService.fillOverseasGap(stockCode, exchangeCode);
                if (calledApi && !sleepBetweenStocks()) {
                    break;
                }
            } catch (Exception e) {
                log.error("해외 가격 갱신 실패(해당 종목만 스킵): stockCode={}, error={}",
                    stockCode, e.getMessage(), e);
            }
            if (needsScoreRefresh(stockCode, latestOverseasPriceDate(stockCode))) {
                needsScoreRefresh.add(stockCode);
            }
        }
        scoreService.recalculateOverseasScores(needsScoreRefresh);
    }

    private boolean sleepBetweenStocks() {
        try {
            Thread.sleep(INTER_STOCK_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("가격 갱신 중단: 인터럽트 발생");
            return false;
        }
    }

    private Optional<LocalDate> latestPriceDate(String stockCode) {
        return dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(DailyPrice::getTradeDate);
    }

    private Optional<LocalDate> latestOverseasPriceDate(String stockCode) {
        return overseasDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(OverseasDailyPrice::getTradeDate);
    }

    /**
     * 스코어의 최신 산출일이 가격의 최신 저장일보다 이미 앞서 있지 않으면
     * (=아직 최신 가격까지 반영 못했으면) 재계산 대상에 포함한다. 가격
     * gap-fill과 달리 스코어 계산 자체는 외부 레이트리밋 대상이 아니지만,
     * 이미 최신인 종목까지 매번 청크에 실어 퀀트 엔진을 부르는 왕복을
     * 아끼기 위한 최소한의 필터다.
     */
    private boolean needsScoreRefresh(String stockCode, Optional<LocalDate> latestPriceDate) {
        if (latestPriceDate.isEmpty()) {
            return false;
        }
        return scoreRepository.findTopByStockCodeOrderByScoreDateDesc(stockCode)
            .map(score -> score.getScoreDate().isBefore(latestPriceDate.get()))
            .orElse(true);
    }
}
