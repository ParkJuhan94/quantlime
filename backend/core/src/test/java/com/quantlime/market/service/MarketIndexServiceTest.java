package com.quantlime.market.service;

import com.quantlime.market.cache.BitcoinChartCache;
import com.quantlime.market.cache.ExchangeRateChartCache;
import com.quantlime.market.cache.DomesticIndexChartCache;
import com.quantlime.market.cache.DomesticIndexMinuteChartCache;
import com.quantlime.market.cache.MarketIndexCache;
import com.quantlime.market.cache.OverseasIndexChartCache;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.market.dto.response.IndexChartResponse;
import com.quantlime.market.dto.response.IndexMinuteChartResponse;
import com.quantlime.market.repository.BenchmarkIndexRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketIndexServiceTest {

    @Mock
    private MarketIndexCache marketIndexCache;

    @Mock
    private DomesticIndexChartCache domesticIndexChartCache;

    @Mock
    private DomesticIndexMinuteChartCache domesticIndexMinuteChartCache;

    @Mock
    private OverseasIndexChartCache overseasIndexChartCache;

    @Mock
    private BitcoinChartCache bitcoinChartCache;

    @Mock
    private ExchangeRateChartCache exchangeRateChartCache;

    @Mock
    private BenchmarkIndexRepository benchmarkIndexRepository;

    @InjectMocks
    private MarketIndexService marketIndexService;

    @Test
    @DisplayName("[국내 지수 차트는 영속 저장된 benchmark_index에서 조회한다 - "
        + "2026-07-30 DomesticIndexChartCache(60초 TTL, 영속 저장 안 함)에서 이관, 종목 상세페이지처럼 영속 이력 조회]")
    void getIndexChart_domestic_readsFromBenchmarkIndex() {
        // given
        given(benchmarkIndexRepository.findByIndexCodeAndTradeDateBetweenOrderByTradeDateAsc(
            eq("KOSPI"), any(), any()))
            .willReturn(List.of(
                BenchmarkIndex.of("KOSPI", LocalDate.of(2026, 7, 15), 7082.91, 7424.18, 7082.91, 7284.41)));

        // when
        List<IndexChartResponse> result = marketIndexService.getIndexChart("KOSPI");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).close()).isEqualTo(7284.41);
        assertThat(result.get(0).tradeDate()).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("[분봉이 있으면 분봉을 그대로 반환한다]")
    void getIndexMinuteChart_minuteChartExists_returnsAsIs() {
        // given
        given(domesticIndexMinuteChartCache.get("KOSPI")).willReturn(
            List.of(new IndexMinuteChartResponse(LocalDate.of(2026, 7, 15).atStartOfDay(), 7284.41)));

        // when
        List<IndexMinuteChartResponse> result = marketIndexService.getIndexMinuteChart("KOSPI");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).price()).isEqualTo(7284.41);
    }

    @Test
    @DisplayName("[분봉이 비어 있으면(장 시작 전·휴장) 최근 일봉으로 폴백한다]")
    void getIndexMinuteChart_minuteChartEmpty_fallsBackToDailyChart() {
        // given
        given(domesticIndexMinuteChartCache.get("KOSPI")).willReturn(List.of());
        given(domesticIndexChartCache.get("KOSPI")).willReturn(List.of(
            new IndexChartResponse(LocalDate.of(2026, 7, 14), 7082.91, 7424.18, 7082.91, 7284.41)));

        // when
        List<IndexMinuteChartResponse> result = marketIndexService.getIndexMinuteChart("KOSPI");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).time()).isEqualTo(LocalDate.of(2026, 7, 14).atStartOfDay());
        assertThat(result.get(0).price()).isEqualTo(7284.41);
    }
}
