package com.quantlime.backtest.service;

import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.exception.BacktestErrorCode;
import com.quantlime.backtest.repository.BacktestDailyScoreRepository;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiResponse;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.market.repository.BenchmarkIndexRepository;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CrossSectionalBacktestServiceTest {

    private static final String SCORE_VERSION = "v2.1";

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private BacktestDailyScoreRepository backtestDailyScoreRepository;

    @Mock
    private BenchmarkIndexRepository benchmarkIndexRepository;

    @Mock
    private PythonEngineClient pythonEngineClient;

    @Mock
    private BacktestPersistenceService backtestPersistenceService;

    @InjectMocks
    private CrossSectionalBacktestService crossSectionalBacktestService;

    @Test
    @DisplayName("[벤치마크 매핑이 없는 시장(코넥스)은 UNSUPPORTED_MARKET 예외를 던진다]")
    void runForMarket_unsupportedMarket_throwsException() {
        assertThatThrownBy(() -> crossSectionalBacktestService.runForMarket(MarketType.KONEX, SCORE_VERSION, false))
            .isInstanceOf(ValidationException.class)
            .hasFieldOrPropertyWithValue("code", BacktestErrorCode.UNSUPPORTED_MARKET.getCode());
        verify(pythonEngineClient, never()).runCrossSectionalBacktest(any());
    }

    @Test
    @DisplayName("[해당 시장에 상장 종목이 없으면 퀀트 엔진을 호출하지 않는다]")
    void runForMarket_noListedStocks_skipsWithoutCallingEngine() {
        // given
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(stock("AAPL", MarketType.NASDAQ)));

        // when
        crossSectionalBacktestService.runForMarket(MarketType.KOSPI, SCORE_VERSION, false);

        // then
        verify(pythonEngineClient, never()).runCrossSectionalBacktest(any());
    }

    @Test
    @DisplayName("[해당 시장 종목의 저장된 스코어가 없으면 퀀트 엔진을 호출하지 않는다]")
    void runForMarket_noPersistedScores_skipsWithoutCallingEngine() {
        // given
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(stock("005930", MarketType.KOSPI)));
        given(backtestDailyScoreRepository
            .findByStockCodeInAndScoreVersionOrderByStockCodeAscTradeDateAsc(any(), anyString()))
            .willReturn(List.of());

        // when
        crossSectionalBacktestService.runForMarket(MarketType.KOSPI, SCORE_VERSION, false);

        // then
        verify(pythonEngineClient, never()).runCrossSectionalBacktest(any());
    }

    @Test
    @DisplayName("[정상 경로에서는 축2 x horizon4=8개 조합을 각각 호출해 저장한다]")
    void runForMarket_happyPath_runsEightCombinationsAndPersistsEach() {
        // given
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(stock("005930", MarketType.KOSPI)));
        given(backtestDailyScoreRepository
            .findByStockCodeInAndScoreVersionOrderByStockCodeAscTradeDateAsc(any(), eq(SCORE_VERSION)))
            .willReturn(List.of(dailyScore("005930")));
        given(benchmarkIndexRepository.findByIndexCodeAndTradeDateBetweenOrderByTradeDateAsc(
            eq("KOSPI"), any(), any())).willReturn(List.of(benchmarkIndex()));
        given(pythonEngineClient.runCrossSectionalBacktest(any())).willReturn(apiResponse(5));

        // when
        crossSectionalBacktestService.runForMarket(MarketType.KOSPI, SCORE_VERSION, false);

        // then
        verify(pythonEngineClient, times(8)).runCrossSectionalBacktest(any());
        verify(backtestPersistenceService, times(8)).saveCrossSectional(any());
    }

    @Test
    @DisplayName("[한 조합의 퀀트 엔진 호출이 실패해도 나머지 조합은 계속 저장된다]")
    void runForMarket_oneCombinationFails_othersStillPersisted() {
        // given
        given(stockMasterService.getAllListedStocks()).willReturn(List.of(stock("005930", MarketType.KOSPI)));
        given(backtestDailyScoreRepository
            .findByStockCodeInAndScoreVersionOrderByStockCodeAscTradeDateAsc(any(), eq(SCORE_VERSION)))
            .willReturn(List.of(dailyScore("005930")));
        given(benchmarkIndexRepository.findByIndexCodeAndTradeDateBetweenOrderByTradeDateAsc(
            eq("KOSPI"), any(), any())).willReturn(List.of(benchmarkIndex()));
        given(pythonEngineClient.runCrossSectionalBacktest(any()))
            .willThrow(new RuntimeException("일시적 장애"))
            .willReturn(apiResponse(10));

        // when: 예외를 던지지 않고 정상적으로 반환되어야 한다
        crossSectionalBacktestService.runForMarket(MarketType.KOSPI, SCORE_VERSION, false);

        // then: 8번 모두 시도되고, 실패한 1건을 제외한 7건만 저장된다
        verify(pythonEngineClient, times(8)).runCrossSectionalBacktest(any());
        verify(backtestPersistenceService, times(7)).saveCrossSectional(any());
    }

    @Test
    @DisplayName("[market 미지정(runAllMarkets)은 4개 지원 시장 전부를 순회한다]")
    void runAllMarkets_runsForAllFourSupportedMarkets() {
        // given: 각 시장 조회에서 종목이 없다고만 응답해 호출 여부만 확인
        given(stockMasterService.getAllListedStocks()).willReturn(List.of());

        // when
        crossSectionalBacktestService.runAllMarkets(SCORE_VERSION, false);

        // then: 시장 4개(KOSPI/KOSDAQ/NASDAQ/NYSE) 각각에 대해 getAllListedStocks가 호출된다
        verify(stockMasterService, times(4)).getAllListedStocks();
    }

    private Stock stock(String code, MarketType marketType) {
        return Stock.of(code, "테스트종목", marketType, ListingStatus.LISTED, null);
    }

    private BacktestDailyScore dailyScore(String stockCode) {
        return BacktestDailyScore.of(
            stockCode, SCORE_VERSION, LocalDate.now(), 70000.0, 60.0, 55.0, null, null);
    }

    private BenchmarkIndex benchmarkIndex() {
        return BenchmarkIndex.of("KOSPI", LocalDate.now(), 2600.0, 2610.0, 2590.0, 2605.0);
    }

    private CrossSectionalBacktestApiResponse apiResponse(int horizon) {
        return new CrossSectionalBacktestApiResponse(
            "KOSPI", SCORE_VERSION, 1, "trend", horizon,
            0.05, -0.02, 0.12, 300, 300, List.of(), null, null, null, null);
    }
}
