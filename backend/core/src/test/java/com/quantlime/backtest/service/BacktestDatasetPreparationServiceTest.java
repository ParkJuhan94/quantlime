package com.quantlime.backtest.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.quantlime.market.service.BenchmarkIndexBackfillService;
import com.quantlime.market.service.DomesticUniverseSelectionService;
import com.quantlime.market.service.OverseasUniverseSelectionService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BacktestDatasetPreparationServiceTest {

    @Mock
    private DomesticUniverseSelectionService domesticUniverseSelectionService;

    @Mock
    private OverseasUniverseSelectionService overseasUniverseSelectionService;

    @Mock
    private BenchmarkIndexBackfillService benchmarkIndexBackfillService;

    @InjectMocks
    private BacktestDatasetPreparationService backtestDatasetPreparationService;

    @Test
    @DisplayName("[국내/해외 유니버스 선정+백필과 벤치마크 백필을 순서대로 전부 실행한다]")
    void prepareDataset_runsAllThreeSteps() {
        // given
        given(domesticUniverseSelectionService.selectAndBackfillUniverse())
            .willReturn(List.of("005930", "000660"));
        given(overseasUniverseSelectionService.selectAndBackfillUniverse())
            .willReturn(List.of("AAPL"));

        // when
        backtestDatasetPreparationService.prepareDataset();

        // then
        verify(domesticUniverseSelectionService).selectAndBackfillUniverse();
        verify(overseasUniverseSelectionService).selectAndBackfillUniverse();
        verify(benchmarkIndexBackfillService).backfillAllIfNeeded();
    }
}
