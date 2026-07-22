package com.quantlime.backtest.service;

import com.quantlime.backtest.domain.BacktestAxis;
import com.quantlime.backtest.domain.BacktestResult;
import com.quantlime.backtest.dto.response.BacktestResponse;
import com.quantlime.backtest.exception.BacktestErrorCode;
import com.quantlime.backtest.repository.BacktestResultRepository;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.BacktestApiResponse;
import com.quantlime.infra.python.dto.BacktestApiResponse.AxisBacktestApiResponse;
import com.quantlime.infra.python.dto.BacktestApiResponse.HorizonStatApiResponse;
import com.quantlime.infra.python.dto.BacktestApiResponse.StabilityStatApiResponse;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.market.repository.BenchmarkIndexRepository;
import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.service.DailyPriceService;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BacktestServiceTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private DailyPriceService dailyPriceService;

    @Mock
    private BenchmarkIndexRepository benchmarkIndexRepository;

    @Mock
    private PythonEngineClient pythonEngineClient;

    @Mock
    private BacktestPersistenceService backtestPersistenceService;

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @InjectMocks
    private BacktestService backtestService;

    @Test
    @DisplayName("[국내(KOSPI) 종목은 벤치마크와 함께 퀀트 엔진에 넘겨 결과를 저장한다]")
    void runBacktest_domesticStock_fetchesDataAndPersists() {
        // given
        given(stockMasterService.getStockByCode(STOCK_CODE)).willReturn(stock(STOCK_CODE, MarketType.KOSPI));
        given(dailyPriceService.getDailyPrices(eq(STOCK_CODE), any(), any()))
            .willReturn(List.of(dailyPrice()));
        given(benchmarkIndexRepository.findByIndexCodeAndTradeDateBetweenOrderByTradeDateAsc(
            eq("KOSPI"), any(), any())).willReturn(List.of(benchmarkIndex()));
        given(pythonEngineClient.runBacktest(any())).willReturn(apiResponse());

        // when
        backtestService.runBacktest(STOCK_CODE);

        // then
        verify(pythonEngineClient).runBacktest(any());
        verify(backtestPersistenceService).saveAll(any());
    }

    @Test
    @DisplayName("[벤치마크가 없는 시장(해외)은 UNSUPPORTED_MARKET 예외를 던진다]")
    void runBacktest_unsupportedMarket_throwsException() {
        // given
        given(stockMasterService.getStockByCode("AAPL")).willReturn(stock("AAPL", MarketType.NASDAQ));

        // when & then
        assertThatThrownBy(() -> backtestService.runBacktest("AAPL"))
            .isInstanceOf(ValidationException.class)
            .hasFieldOrPropertyWithValue("code", BacktestErrorCode.UNSUPPORTED_MARKET.getCode());
        verify(pythonEngineClient, never()).runBacktest(any());
    }

    @Test
    @DisplayName("[OHLCV 이력이 없으면 INSUFFICIENT_HISTORY 예외를 던진다]")
    void runBacktest_noDailyPriceHistory_throwsException() {
        // given
        given(stockMasterService.getStockByCode(STOCK_CODE)).willReturn(stock(STOCK_CODE, MarketType.KOSPI));
        given(dailyPriceService.getDailyPrices(eq(STOCK_CODE), any(), any())).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> backtestService.runBacktest(STOCK_CODE))
            .isInstanceOf(ValidationException.class)
            .hasFieldOrPropertyWithValue("code", BacktestErrorCode.INSUFFICIENT_HISTORY.getCode());
        verify(pythonEngineClient, never()).runBacktest(any());
    }

    @Test
    @DisplayName("[벤치마크 이력이 없으면 INSUFFICIENT_HISTORY 예외를 던진다]")
    void runBacktest_noBenchmarkHistory_throwsException() {
        // given
        given(stockMasterService.getStockByCode(STOCK_CODE)).willReturn(stock(STOCK_CODE, MarketType.KOSPI));
        given(dailyPriceService.getDailyPrices(eq(STOCK_CODE), any(), any()))
            .willReturn(List.of(dailyPrice()));
        given(benchmarkIndexRepository.findByIndexCodeAndTradeDateBetweenOrderByTradeDateAsc(
            eq("KOSPI"), any(), any())).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> backtestService.runBacktest(STOCK_CODE))
            .isInstanceOf(ValidationException.class)
            .hasFieldOrPropertyWithValue("code", BacktestErrorCode.INSUFFICIENT_HISTORY.getCode());
    }

    @Test
    @DisplayName("[백테스트 결과가 없으면 NOT_FOUND 예외를 던진다]")
    void getBacktestResult_noResults_throwsNotFoundException() {
        // given
        given(backtestResultRepository.findTopByStockCodeOrderByBacktestDateDesc(STOCK_CODE))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> backtestService.getBacktestResult(STOCK_CODE))
            .isInstanceOf(NotFoundException.class)
            .hasFieldOrPropertyWithValue("code", BacktestErrorCode.NOT_FOUND_BACKTEST_RESULT.getCode());
    }

    @Test
    @DisplayName("[가장 최근 스코어 버전 기준으로 결과를 조회해 응답으로 변환한다]")
    void getBacktestResult_returnsLatestVersionAsResponse() {
        // given
        BacktestResult latest = BacktestResult.of(
            STOCK_CODE, BacktestAxis.TREND, 5, "v2.1", LocalDate.now(),
            300, 0.1, -0.1, 0.3, 0.05, 0.1, List.of());
        given(backtestResultRepository.findTopByStockCodeOrderByBacktestDateDesc(STOCK_CODE))
            .willReturn(Optional.of(latest));
        given(backtestResultRepository
            .findByStockCodeAndScoreVersionOrderByAxisAscHorizonDaysAsc(STOCK_CODE, "v2.1"))
            .willReturn(List.of(latest));

        // when
        BacktestResponse response = backtestService.getBacktestResult(STOCK_CODE);

        // then
        assertThat(response.stockCode()).isEqualTo(STOCK_CODE);
        assertThat(response.scoreVersion()).isEqualTo("v2.1");
        assertThat(response.axes()).hasSize(1);
    }

    private Stock stock(String code, MarketType marketType) {
        return Stock.of(code, "테스트종목", marketType, ListingStatus.LISTED, null);
    }

    private DailyPrice dailyPrice() {
        return DailyPrice.of(STOCK_CODE, LocalDate.now(), 100L, 105L, 95L, 100L, 1000L);
    }

    private BenchmarkIndex benchmarkIndex() {
        return BenchmarkIndex.of("KOSPI", LocalDate.now(), 2600.0, 2610.0, 2590.0, 2605.0);
    }

    private BacktestApiResponse apiResponse() {
        return new BacktestApiResponse(
            STOCK_CODE, "v2.1", 300,
            List.of(new AxisBacktestApiResponse(
                "trend",
                List.of(new HorizonStatApiResponse(5, 0.1, -0.1, 0.3, 300, List.of())),
                new StabilityStatApiResponse(0.05, 0.1)
            ))
        );
    }
}
