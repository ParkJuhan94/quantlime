package com.quantlime.market.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.naver.NaverFinanceApiClient;
import com.quantlime.infra.naver.dto.NaverIndexCandleResponse;
import com.quantlime.market.domain.WorldIndexCode;
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

    @Test
    @DisplayName("[refreshRecentIfNeeded는 이미 목표치가 쌓여 있어도 최신 페이지를 조회한다 - "
        + "backfillIfNeeded와 달리 기존 건수 체크로 스킵하지 않는다(2026-07-30 실제 버그: "
        + "이 스킵 로직 때문에 KOSPI 벤치마크가 2주째 최신화되지 않아 등락률이 틀어졌었음)]")
    void refreshRecentIfNeeded_alwaysFetchesLatestPageRegardlessOfExistingCount() {
        // given: countByIndexCode를 스텁하지 않음 - 호출되면 안 됨을 아래에서 검증
        given(naverFinanceApiClient.getIndexPrices(eq("KOSPI"), eq(60))).willReturn(page(2, "2026-07-30"));
        given(naverFinanceApiClient.getIndexPrices(eq("KOSDAQ"), eq(60))).willReturn(page(1, "2026-07-30"));
        given(naverFinanceApiClient.getWorldIndexPrices(any(), eq(60))).willReturn(List.of());
        given(benchmarkIndexRepository.existsByIndexCodeAndTradeDate(any(), any())).willReturn(false);

        // when
        benchmarkIndexBackfillService.refreshRecentIfNeeded();

        // then: 목표건수 체크(countByIndexCode) 없이 바로 최신 페이지를 저장
        verify(benchmarkIndexRepository, never()).countByIndexCode(any());
        verify(benchmarkIndexRepository, times(2)).save(argThat(b -> b.getIndexCode().equals("KOSPI")));
        verify(benchmarkIndexRepository, times(1)).save(argThat(b -> b.getIndexCode().equals("KOSDAQ")));
    }

    @Test
    @DisplayName("[refreshRecentIfNeeded는 이미 저장된 날짜는 다시 저장하지 않는다]")
    void refreshRecentIfNeeded_existingDate_skipsSave() {
        // given
        given(naverFinanceApiClient.getIndexPrices(eq("KOSPI"), eq(60))).willReturn(page(2, "2026-07-30"));
        given(naverFinanceApiClient.getIndexPrices(eq("KOSDAQ"), eq(60))).willReturn(page(2, "2026-07-30"));
        given(naverFinanceApiClient.getWorldIndexPrices(any(), eq(60))).willReturn(List.of());
        given(benchmarkIndexRepository.existsByIndexCodeAndTradeDate(any(), any())).willReturn(true);

        // when
        benchmarkIndexBackfillService.refreshRecentIfNeeded();

        // then
        verify(benchmarkIndexRepository, never()).save(any());
    }

    @Test
    @DisplayName("[refreshRecentIfNeeded는 해외 지수(나스닥/S&P500)도 목표건수 체크 없이 최신 페이지를 갱신한다 - "
        + "국내만 고쳐졌던 2026-07-30 수정에서 해외가 빠져 NASDAQ/SP500 벤치마크가 계속 갭인 채로 "
        + "남아있던 걸 2026-07-31에 마저 수정]")
    void refreshRecentIfNeeded_alwaysFetchesLatestOverseasPageRegardlessOfExistingCount() {
        // given: 국내는 빈 페이지로 스텁, 해외만 검증 대상
        given(naverFinanceApiClient.getIndexPrices(any(), eq(60))).willReturn(List.of());
        given(naverFinanceApiClient.getWorldIndexPrices(eq(WorldIndexCode.NASDAQ.getReutersCode()), eq(60)))
            .willReturn(page(2, "2026-07-31"));
        given(naverFinanceApiClient.getWorldIndexPrices(eq(WorldIndexCode.SP500.getReutersCode()), eq(60)))
            .willReturn(page(1, "2026-07-31"));
        given(benchmarkIndexRepository.existsByIndexCodeAndTradeDate(any(), any())).willReturn(false);

        // when
        benchmarkIndexBackfillService.refreshRecentIfNeeded();

        // then
        verify(benchmarkIndexRepository, never()).countByIndexCode(any());
        verify(benchmarkIndexRepository, times(2)).save(argThat(b -> b.getIndexCode().equals("NASDAQ")));
        verify(benchmarkIndexRepository, times(1)).save(argThat(b -> b.getIndexCode().equals("SP500")));
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
