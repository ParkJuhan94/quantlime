package com.quantlime.backtest.service;

import com.quantlime.backtest.dto.mapper.BacktestMapper;
import com.quantlime.backtest.dto.mapper.BacktestRequestMapper;
import com.quantlime.backtest.dto.response.BacktestResponse;
import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.domain.BacktestResult;
import com.quantlime.backtest.exception.BacktestErrorCode;
import com.quantlime.backtest.repository.BacktestDailyScoreRepository;
import com.quantlime.backtest.repository.BacktestResultRepository;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.BacktestApiRequest;
import com.quantlime.infra.python.dto.BacktestApiResponse;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.market.repository.BenchmarkIndexRepository;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.service.DomesticDailyPriceService;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목의 OHLCV/벤치마크 이력을 퀀트 엔진(Python)에 넘겨 백테스트를 실행하고
 * 결과를 영속화한다 - {@link com.quantlime.score.service.ScoreService}와
 * 동일한 구조(OHLCV 조회 + 외부 호출은 트랜잭션 밖, 저장만 별도 트랜잭션).
 *
 * <p>국내(KOSPI/KOSDAQ)는 daily_price, 해외(NASDAQ/NYSE)는
 * overseas_daily_price에서 OHLCV를 조회한다(가격 컬럼 타입이 Long/Double로
 * 달라 엔티티 자체가 분리돼 있음 - PriceGapFillService와 동일한 이유).
 * 벤치마크 지수(KOSPI/KOSDAQ/NASDAQ/SP500) 매핑이 없는 시장(코넥스)만
 * {@link BacktestErrorCode#UNSUPPORTED_MARKET}로 명확히 실패시킨다(조용히
 * 빈 결과를 반환하지 않음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestService {

    // 백테스트 유니버스 심화 백필 목표(400일, DomesticUniverseSelectionService)에
    // 맞춰, 거래일 400일을 담고도 남을 만큼 넉넉한 달력일 범위를 조회한다.
    private static final int OHLCV_LOOKBACK_CALENDAR_DAYS = 600;
    // 나스닥 상장은 나스닥종합, 뉴욕 상장은 S&P500과 비교하는 통상적 매핑
    // (BenchmarkIndexBackfillService가 이 두 지수를 국내와 동일하게 백필).
    private static final Map<MarketType, String> BENCHMARK_INDEX_CODE = Map.of(
        MarketType.KOSPI, "KOSPI",
        MarketType.KOSDAQ, "KOSDAQ",
        MarketType.NASDAQ, "NASDAQ",
        MarketType.NYSE, "SP500"
    );

    private final StockMasterService stockMasterService;
    private final DomesticDailyPriceService domesticDailyPriceService;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;
    private final BenchmarkIndexRepository benchmarkIndexRepository;
    private final PythonEngineClient pythonEngineClient;
    private final BacktestPersistenceService backtestPersistenceService;
    private final BacktestResultRepository backtestResultRepository;
    private final BacktestDailyScoreRepository backtestDailyScoreRepository;

    public void runBacktest(String stockCode) {
        Stock stock = stockMasterService.getStockByCode(stockCode);
        String benchmarkIndexCode = BENCHMARK_INDEX_CODE.get(stock.getMarketType());
        if (benchmarkIndexCode == null) {
            throw new ValidationException(BacktestErrorCode.UNSUPPORTED_MARKET);
        }

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(OHLCV_LOOKBACK_CALENDAR_DAYS);
        List<BenchmarkIndex> benchmarkPrices = benchmarkIndexRepository
            .findByIndexCodeAndTradeDateBetweenOrderByTradeDateAsc(benchmarkIndexCode, start, end);

        BacktestApiRequest request = stock.getMarketType().isDomestic()
            ? toDomesticRequest(stockCode, start, end, benchmarkPrices)
            : toOverseasRequest(stockCode, start, end, benchmarkPrices);
        BacktestApiResponse response = pythonEngineClient.runBacktest(request);

        List<BacktestResult> results = BacktestMapper.toBacktestResults(response, end);
        backtestPersistenceService.saveAll(results);

        List<BacktestDailyScore> dailyScores = BacktestMapper.toBacktestDailyScores(response);
        backtestPersistenceService.replaceDailyScores(stockCode, response.scoreVersion(), dailyScores);

        log.info("백테스트 완료: stockCode={}, scoreVersion={}, sampleDays={}",
            stockCode, response.scoreVersion(), response.sampleDays());
    }

    private BacktestApiRequest toDomesticRequest(
        String stockCode, LocalDate start, LocalDate end, List<BenchmarkIndex> benchmarkPrices) {
        List<DomesticDailyPrice> domesticDailyPrices = domesticDailyPriceService.getDailyPrices(stockCode, start, end);
        if (domesticDailyPrices.isEmpty() || benchmarkPrices.isEmpty()) {
            throw new ValidationException(BacktestErrorCode.INSUFFICIENT_HISTORY);
        }
        return BacktestRequestMapper.toBacktestApiRequest(stockCode, domesticDailyPrices, benchmarkPrices);
    }

    private BacktestApiRequest toOverseasRequest(
        String stockCode, LocalDate start, LocalDate end, List<BenchmarkIndex> benchmarkPrices) {
        List<OverseasDailyPrice> overseasDailyPrices = overseasDailyPriceRepository
            .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, start, end);
        if (overseasDailyPrices.isEmpty() || benchmarkPrices.isEmpty()) {
            throw new ValidationException(BacktestErrorCode.INSUFFICIENT_HISTORY);
        }
        return BacktestRequestMapper.toOverseasBacktestApiRequest(stockCode, overseasDailyPrices, benchmarkPrices);
    }

    @Transactional(readOnly = true)
    public BacktestResponse getBacktestResult(String stockCode) {
        String latestVersion = backtestResultRepository.findTopByStockCodeOrderByBacktestDateDesc(stockCode)
            .orElseThrow(() -> new NotFoundException(BacktestErrorCode.NOT_FOUND_BACKTEST_RESULT))
            .getScoreVersion();

        List<BacktestResult> results = backtestResultRepository
            .findByStockCodeAndScoreVersionOrderByAxisAscHorizonDaysAsc(stockCode, latestVersion);
        List<BacktestDailyScore> dailyScores = backtestDailyScoreRepository
            .findByStockCodeAndScoreVersionOrderByTradeDateAsc(stockCode, latestVersion);
        return BacktestMapper.toBacktestResponse(stockCode, latestVersion, results, dailyScores);
    }
}
