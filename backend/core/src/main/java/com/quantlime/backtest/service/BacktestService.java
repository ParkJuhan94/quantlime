package com.quantlime.backtest.service;

import com.quantlime.backtest.dto.mapper.BacktestMapper;
import com.quantlime.backtest.dto.mapper.BacktestRequestMapper;
import com.quantlime.backtest.dto.response.BacktestResponse;
import com.quantlime.backtest.domain.BacktestResult;
import com.quantlime.backtest.exception.BacktestErrorCode;
import com.quantlime.backtest.repository.BacktestResultRepository;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.BacktestApiRequest;
import com.quantlime.infra.python.dto.BacktestApiResponse;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.market.repository.BenchmarkIndexRepository;
import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.service.DailyPriceService;
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
 * <p>벤치마크는 현재 국내 지수(KOSPI/KOSDAQ)만 Phase B에서 백필돼 있어
 * (CLAUDE.md 백테스트 계획 참고), 해외(NASDAQ/NYSE) 종목은 아직 백테스트를
 * 지원하지 않는다 - 지원 시장이 아니면 {@link BacktestErrorCode#UNSUPPORTED_MARKET}로
 * 명확히 실패시킨다(조용히 빈 결과를 반환하지 않음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestService {

    // 백테스트 유니버스 심화 백필 목표(400일, DomesticUniverseSelectionService)에
    // 맞춰, 거래일 400일을 담고도 남을 만큼 넉넉한 달력일 범위를 조회한다.
    private static final int OHLCV_LOOKBACK_CALENDAR_DAYS = 600;
    private static final Map<MarketType, String> BENCHMARK_INDEX_CODE = Map.of(
        MarketType.KOSPI, "KOSPI",
        MarketType.KOSDAQ, "KOSDAQ"
    );

    private final StockMasterService stockMasterService;
    private final DailyPriceService dailyPriceService;
    private final BenchmarkIndexRepository benchmarkIndexRepository;
    private final PythonEngineClient pythonEngineClient;
    private final BacktestPersistenceService backtestPersistenceService;
    private final BacktestResultRepository backtestResultRepository;

    public void runBacktest(String stockCode) {
        Stock stock = stockMasterService.getStockByCode(stockCode);
        String benchmarkIndexCode = BENCHMARK_INDEX_CODE.get(stock.getMarketType());
        if (benchmarkIndexCode == null) {
            throw new ValidationException(BacktestErrorCode.UNSUPPORTED_MARKET);
        }

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(OHLCV_LOOKBACK_CALENDAR_DAYS);
        List<DailyPrice> dailyPrices = dailyPriceService.getDailyPrices(stockCode, start, end);
        List<BenchmarkIndex> benchmarkPrices = benchmarkIndexRepository
            .findByIndexCodeAndTradeDateBetweenOrderByTradeDateAsc(benchmarkIndexCode, start, end);

        if (dailyPrices.isEmpty() || benchmarkPrices.isEmpty()) {
            throw new ValidationException(BacktestErrorCode.INSUFFICIENT_HISTORY);
        }

        BacktestApiRequest request =
            BacktestRequestMapper.toBacktestApiRequest(stockCode, dailyPrices, benchmarkPrices);
        BacktestApiResponse response = pythonEngineClient.runBacktest(request);

        List<BacktestResult> results = BacktestMapper.toBacktestResults(response, end);
        backtestPersistenceService.saveAll(results);

        log.info("백테스트 완료: stockCode={}, scoreVersion={}, sampleDays={}",
            stockCode, response.scoreVersion(), response.sampleDays());
    }

    @Transactional(readOnly = true)
    public BacktestResponse getBacktestResult(String stockCode) {
        String latestVersion = backtestResultRepository.findTopByStockCodeOrderByBacktestDateDesc(stockCode)
            .orElseThrow(() -> new NotFoundException(BacktestErrorCode.NOT_FOUND_BACKTEST_RESULT))
            .getScoreVersion();

        List<BacktestResult> results = backtestResultRepository
            .findByStockCodeAndScoreVersionOrderByAxisAscHorizonDaysAsc(stockCode, latestVersion);
        return BacktestMapper.toBacktestResponse(stockCode, latestVersion, results);
    }
}
