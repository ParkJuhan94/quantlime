package com.quantlime.market.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.naver.NaverFinanceApiClient;
import com.quantlime.infra.naver.dto.NaverIndexCandleResponse;
import com.quantlime.market.repository.BenchmarkIndexRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BenchmarkIndexBackfillServiceTest {

    private static final String INDEX_CODE = "KOSPI";

    @Mock
    private BenchmarkIndexRepository benchmarkIndexRepository;

    @Mock
    private NaverFinanceApiClient naverFinanceApiClient;

    @InjectMocks
    private BenchmarkIndexBackfillService benchmarkIndexBackfillService;

    @Test
    @DisplayName("[기존 이력이 목표 일수 이상이면 API를 호출하지 않는다]")
    void backfillIfNeeded_alreadySufficient_doesNotCallApi() {
        // given
        given(benchmarkIndexRepository.countByIndexCode(INDEX_CODE)).willReturn(400L);

        // when
        benchmarkIndexBackfillService.backfillIfNeeded(INDEX_CODE, 400);

        // then
        verify(naverFinanceApiClient, never()).getIndexPrices(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("[짧은 페이지(마지막 페이지)를 받으면 백필을 종료한다]")
    void backfillIfNeeded_shortPage_stopsAfterFirstPage() {
        // given
        given(benchmarkIndexRepository.countByIndexCode(INDEX_CODE)).willReturn(0L);
        given(naverFinanceApiClient.getIndexPrices(eq(INDEX_CODE), eq(60), eq(1)))
            .willReturn(page(10, "2026-07-16"));
        given(benchmarkIndexRepository.existsByIndexCodeAndTradeDate(eq(INDEX_CODE), any()))
            .willReturn(false);

        // when
        benchmarkIndexBackfillService.backfillIfNeeded(INDEX_CODE, 400);

        // then: 10건(<60) 받았으니 page=2는 호출하지 않는다
        verify(naverFinanceApiClient, times(1)).getIndexPrices(any(), anyInt(), anyInt());
        verify(benchmarkIndexRepository, times(10)).save(any());
    }

    @Test
    @DisplayName("[이미 저장된 거래일은 다시 저장하지 않는다]")
    void backfillIfNeeded_existingDate_skipsSave() {
        // given
        given(benchmarkIndexRepository.countByIndexCode(INDEX_CODE)).willReturn(0L);
        given(naverFinanceApiClient.getIndexPrices(eq(INDEX_CODE), eq(60), eq(1)))
            .willReturn(page(5, "2026-07-16"));
        given(benchmarkIndexRepository.existsByIndexCodeAndTradeDate(eq(INDEX_CODE), eq(LocalDate.parse("2026-07-16"))))
            .willReturn(true);
        given(benchmarkIndexRepository.existsByIndexCodeAndTradeDate(eq(INDEX_CODE), eq(LocalDate.parse("2026-07-15"))))
            .willReturn(false);
        given(benchmarkIndexRepository.existsByIndexCodeAndTradeDate(eq(INDEX_CODE), eq(LocalDate.parse("2026-07-14"))))
            .willReturn(false);
        given(benchmarkIndexRepository.existsByIndexCodeAndTradeDate(eq(INDEX_CODE), eq(LocalDate.parse("2026-07-13"))))
            .willReturn(false);
        given(benchmarkIndexRepository.existsByIndexCodeAndTradeDate(eq(INDEX_CODE), eq(LocalDate.parse("2026-07-12"))))
            .willReturn(false);

        // when
        benchmarkIndexBackfillService.backfillIfNeeded(INDEX_CODE, 400);

        // then: 5건 중 1건(07-16)은 이미 존재해 4건만 저장
        verify(benchmarkIndexRepository, times(4)).save(any());
    }

    /** localTradedAt이 startDate부터 하루씩 과거로 내려가는 candle 목록. */
    private List<NaverIndexCandleResponse> page(int size, String startDate) {
        LocalDate start = LocalDate.parse(startDate);
        return IntStream.range(0, size)
            .mapToObj(i -> new NaverIndexCandleResponse(
                start.minusDays(i).toString(), "2,800.00", "2,790.00", "2,810.00", "2,780.00"))
            .toList();
    }
}
