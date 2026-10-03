package com.quantlime.market.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.quantlime.market.domain.OverseasIndexCode;
import com.quantlime.market.implement.BenchmarkIndexCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 어떤 지수를 갱신할지(순회 대상)만 검증한다 - 수집·저장 자체는 BenchmarkIndexCollectorTest. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BenchmarkIndexBackfillServiceTest {

    @Mock
    private BenchmarkIndexCollector benchmarkIndexCollector;

    @InjectMocks
    private BenchmarkIndexBackfillService benchmarkIndexBackfillService;

    @Test
    @DisplayName("[refreshRecentIfNeeded는 국내(KOSPI/KOSDAQ)와 해외(NASDAQ/SP500) 4개 지수를 모두 갱신한다]")
    void refreshRecentIfNeeded_refreshesAllDomesticAndOverseasIndices() {
        benchmarkIndexBackfillService.refreshRecentIfNeeded();

        verify(benchmarkIndexCollector).refreshRecentDomestic("KOSPI");
        verify(benchmarkIndexCollector).refreshRecentDomestic("KOSDAQ");
        verify(benchmarkIndexCollector).refreshRecentWorld("NASDAQ", OverseasIndexCode.NASDAQ);
        verify(benchmarkIndexCollector).refreshRecentWorld("SP500", OverseasIndexCode.SP500);
        verifyNoMoreInteractions(benchmarkIndexCollector);
    }

    @Test
    @DisplayName("[backfillAllIfNeeded는 4개 지수를 목표 400일로 백필한다]")
    void backfillAllIfNeeded_backfillsAllIndicesToTargetDays() {
        benchmarkIndexBackfillService.backfillAllIfNeeded();

        verify(benchmarkIndexCollector).backfillDomestic("KOSPI", 400);
        verify(benchmarkIndexCollector).backfillDomestic("KOSDAQ", 400);
        verify(benchmarkIndexCollector).backfillWorld("NASDAQ", OverseasIndexCode.NASDAQ, 400);
        verify(benchmarkIndexCollector).backfillWorld("SP500", OverseasIndexCode.SP500, 400);
        verifyNoMoreInteractions(benchmarkIndexCollector);
    }

    @Test
    @DisplayName("[개별 지수 백필 진입점은 Collector로 그대로 위임한다]")
    void backfillEntryPoints_delegateToCollector() {
        benchmarkIndexBackfillService.backfillIfNeeded("KOSPI", 100);
        benchmarkIndexBackfillService.backfillOverseasIfNeeded("NASDAQ", OverseasIndexCode.NASDAQ, 50);

        verify(benchmarkIndexCollector).backfillDomestic("KOSPI", 100);
        verify(benchmarkIndexCollector).backfillWorld("NASDAQ", OverseasIndexCode.NASDAQ, 50);
    }
}
