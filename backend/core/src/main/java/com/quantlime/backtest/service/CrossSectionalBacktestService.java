package com.quantlime.backtest.service;

import com.quantlime.backtest.domain.BacktestAxis;
import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.domain.BacktestSampleSplit;
import com.quantlime.backtest.domain.CrossSectionalBacktestResult;
import com.quantlime.backtest.dto.mapper.CrossSectionalBacktestMapper;
import com.quantlime.backtest.exception.BacktestErrorCode;
import com.quantlime.backtest.repository.BacktestDailyScoreRepository;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiRequest;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiResponse;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.market.repository.BenchmarkIndexRepository;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 시장 하나에 대해 종목 하나의 시간축이 아니라 "같은 날짜의 여러 종목"을
 * 줄세워 비교하는 횡단면(cross-sectional) Rank IC를 계산한다 -
 * {@link BacktestService}(종목별 시계열 IC)와 답하는 질문이 다르다
 * (2026-08 감사 세션, docs/CHANGELOG.md 참고). 이미 {@code backtest_daily_score}에
 * 저장된 스코어를 그대로 쓰므로 OHLCV 재조회·재스코어링이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossSectionalBacktestService {

    // BacktestService.BENCHMARK_INDEX_CODE와 동일한 매핑이다 - 그쪽이
    // private라 재사용할 수 없어 작게 복제한다(4개 고정 매핑이라 변경
    // 가능성이 낮아 별도 공유 상수로 추출하는 비용이 더 크다고 판단).
    private static final Map<MarketType, String> BENCHMARK_INDEX_CODE = Map.of(
        MarketType.KOSPI, "KOSPI",
        MarketType.KOSDAQ, "KOSDAQ",
        MarketType.NASDAQ, "NASDAQ",
        MarketType.NYSE, "SP500"
    );
    private static final List<Integer> HORIZONS = List.of(5, 10, 20, 60);
    // BacktestService.OHLCV_LOOKBACK_CALENDAR_DAYS와 동일 - 벤치마크 조회
    // 구간을 종목 스코어 이력 구간과 맞춰야 inner join에서 손실이 없다.
    private static final int LOOKBACK_CALENDAR_DAYS = 750;
    private static final int NULL_TEST_REPEATS = 200;

    private final StockMasterService stockMasterService;
    private final BacktestDailyScoreRepository backtestDailyScoreRepository;
    private final BenchmarkIndexRepository benchmarkIndexRepository;
    private final PythonEngineClient pythonEngineClient;
    private final BacktestPersistenceService backtestPersistenceService;

    /**
     * 시장 하나에 대해 (축 2 x horizon 4=)8개 조합을 순차 호출한다. 조합
     * 하나의 실패가 나머지를 막지 않도록 항목별로 예외를 격리한다
     * (ScorePersistenceService.saveAll과 동일한 패턴). 호출을 조합 단위로
     * 좁힌 이유는 quant-engine의 CrossSectionalBacktestRequest 문서 참고
     * (500종목 규모에서 조합을 한 호출에 몰아넣으면 read timeout을 넘김).
     */
    public void runForMarket(MarketType market, String scoreVersion, boolean nullTest) {
        String benchmarkIndexCode = BENCHMARK_INDEX_CODE.get(market);
        if (benchmarkIndexCode == null) {
            throw new ValidationException(BacktestErrorCode.UNSUPPORTED_MARKET);
        }

        List<String> stockCodes = stockMasterService.getAllListedStocks().stream()
            .filter(stock -> stock.getMarketType() == market)
            .map(Stock::getStockCode)
            .toList();
        if (stockCodes.isEmpty()) {
            log.warn("횡단면 백테스트 대상 종목 없음: market={}", market);
            return;
        }

        Map<String, List<BacktestDailyScore>> dailyScoresByStock = backtestDailyScoreRepository
            .findByStockCodeInAndScoreVersionOrderByStockCodeAscTradeDateAsc(stockCodes, scoreVersion)
            .stream()
            .collect(Collectors.groupingBy(BacktestDailyScore::getStockCode));
        if (dailyScoresByStock.isEmpty()) {
            log.warn("횡단면 백테스트용 저장된 스코어 없음: market={}, scoreVersion={}", market, scoreVersion);
            return;
        }

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(LOOKBACK_CALENDAR_DAYS);
        List<BenchmarkIndex> benchmarkPrices = benchmarkIndexRepository
            .findByIndexCodeAndTradeDateBetweenOrderByTradeDateAsc(benchmarkIndexCode, start, end);

        for (BacktestAxis axis : BacktestAxis.values()) {
            for (int horizonDays : HORIZONS) {
                runOne(market, scoreVersion, dailyScoresByStock, benchmarkPrices, axis, horizonDays, nullTest);
            }
        }
    }

    public void runAllMarkets(String scoreVersion, boolean nullTest) {
        BENCHMARK_INDEX_CODE.keySet().forEach(market -> runForMarket(market, scoreVersion, nullTest));
    }

    private void runOne(MarketType market, String scoreVersion,
                        Map<String, List<BacktestDailyScore>> dailyScoresByStock,
                        List<BenchmarkIndex> benchmarkPrices, BacktestAxis axis, int horizonDays,
                        boolean nullTest) {
        try {
            CrossSectionalBacktestApiRequest request = CrossSectionalBacktestMapper.toApiRequest(
                market, scoreVersion, dailyScoresByStock, benchmarkPrices, axis, horizonDays,
                nullTest, NULL_TEST_REPEATS);
            CrossSectionalBacktestApiResponse response = pythonEngineClient.runCrossSectionalBacktest(request);
            CrossSectionalBacktestResult result = CrossSectionalBacktestMapper.toResult(
                market, axis, BacktestSampleSplit.FULL, LocalDate.now(), response);
            backtestPersistenceService.saveCrossSectional(result);
            log.info("횡단면 백테스트 완료: market={}, axis={}, horizonDays={}, meanIc={}, nDates={}",
                market, axis, horizonDays, result.getMeanIc(), result.getSampleDates());
        } catch (Exception e) {
            log.error("횡단면 백테스트 실패(해당 조합만 스킵): market={}, axis={}, horizonDays={}, error={}",
                market, axis, horizonDays, e.getMessage(), e);
        }
    }
}
