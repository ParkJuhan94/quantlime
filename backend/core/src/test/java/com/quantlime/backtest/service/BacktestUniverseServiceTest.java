package com.quantlime.backtest.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.backtest.domain.BacktestAxis;
import com.quantlime.backtest.domain.BacktestResult;
import com.quantlime.backtest.repository.BacktestResultRepository;
import com.quantlime.market.service.DomesticUniverseSelectionService;
import com.quantlime.market.service.OverseasUniverseSelectionService;
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

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BacktestUniverseServiceTest {

    @Mock
    private DomesticUniverseSelectionService domesticUniverseSelectionService;

    @Mock
    private OverseasUniverseSelectionService overseasUniverseSelectionService;

    @Mock
    private BacktestService backtestService;

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @InjectMocks
    private BacktestUniverseService backtestUniverseService;

    @Test
    @DisplayName("[국내+해외 유니버스 전체에 대해 백테스트를 실행한다]")
    void runUniverse_runsBacktestForEveryStockInBothUniverses() {
        // given
        given(domesticUniverseSelectionService.selectAndBackfillUniverse())
            .willReturn(List.of("005930", "000660"));
        given(overseasUniverseSelectionService.selectAndBackfillUniverse())
            .willReturn(List.of("AAPL"));
        given(backtestResultRepository.findTopByStockCodeOrderByBacktestDateDesc(org.mockito.ArgumentMatchers.any()))
            .willReturn(Optional.empty());

        // when
        backtestUniverseService.runUniverse();

        // then
        verify(backtestService).runBacktest("005930");
        verify(backtestService).runBacktest("000660");
        verify(backtestService).runBacktest("AAPL");
    }

    @Test
    @DisplayName("[오늘 이미 백테스트가 돈 종목은 건너뛴다]")
    void runUniverse_alreadyRanToday_skipsStock() {
        // given
        given(domesticUniverseSelectionService.selectAndBackfillUniverse())
            .willReturn(List.of("005930"));
        given(overseasUniverseSelectionService.selectAndBackfillUniverse())
            .willReturn(List.of());
        BacktestResult ranToday = BacktestResult.of(
            "005930", BacktestAxis.TREND, 5, "v2.1", LocalDate.now(),
            300, 0.1, -0.1, 0.3, 0.05, 0.1, List.of());
        given(backtestResultRepository.findTopByStockCodeOrderByBacktestDateDesc(eq("005930")))
            .willReturn(Optional.of(ranToday));

        // when
        backtestUniverseService.runUniverse();

        // then
        verify(backtestService, never()).runBacktest("005930");
    }

    @Test
    @DisplayName("[어제 실행된 종목은 오늘 다시 실행한다]")
    void runUniverse_ranYesterday_runsAgainToday() {
        // given
        given(domesticUniverseSelectionService.selectAndBackfillUniverse())
            .willReturn(List.of("005930"));
        given(overseasUniverseSelectionService.selectAndBackfillUniverse())
            .willReturn(List.of());
        BacktestResult ranYesterday = BacktestResult.of(
            "005930", BacktestAxis.TREND, 5, "v2.1", LocalDate.now().minusDays(1),
            300, 0.1, -0.1, 0.3, 0.05, 0.1, List.of());
        given(backtestResultRepository.findTopByStockCodeOrderByBacktestDateDesc(eq("005930")))
            .willReturn(Optional.of(ranYesterday));

        // when
        backtestUniverseService.runUniverse();

        // then
        verify(backtestService, times(1)).runBacktest("005930");
    }

    @Test
    @DisplayName("[한 종목의 백테스트가 실패해도 나머지 종목은 계속 실행된다]")
    void runUniverse_oneStockFails_othersStillRun() {
        // given
        given(domesticUniverseSelectionService.selectAndBackfillUniverse())
            .willReturn(List.of("005930", "000660"));
        given(overseasUniverseSelectionService.selectAndBackfillUniverse())
            .willReturn(List.of());
        given(backtestResultRepository.findTopByStockCodeOrderByBacktestDateDesc(org.mockito.ArgumentMatchers.any()))
            .willReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new RuntimeException("퀀트 엔진 실패"))
            .when(backtestService).runBacktest("005930");

        // when: 예외 없이 정상 종료돼야 한다
        backtestUniverseService.runUniverse();

        // then
        verify(backtestService).runBacktest("000660");
    }
}
