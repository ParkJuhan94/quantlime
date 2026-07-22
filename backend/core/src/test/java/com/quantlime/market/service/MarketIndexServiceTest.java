package com.quantlime.market.service;

import com.quantlime.market.cache.BitcoinChartCache;
import com.quantlime.market.cache.ExchangeRateChartCache;
import com.quantlime.market.cache.IndexChartCache;
import com.quantlime.market.cache.IndexMinuteChartCache;
import com.quantlime.market.cache.MarketIndexCache;
import com.quantlime.market.cache.WorldIndexChartCache;
import com.quantlime.market.dto.response.IndexChartResponse;
import com.quantlime.market.dto.response.IndexMinuteChartResponse;
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
import static org.mockito.BDDMockito.given;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketIndexServiceTest {

    @Mock
    private MarketIndexCache marketIndexCache;

    @Mock
    private IndexChartCache indexChartCache;

    @Mock
    private IndexMinuteChartCache indexMinuteChartCache;

    @Mock
    private WorldIndexChartCache worldIndexChartCache;

    @Mock
    private BitcoinChartCache bitcoinChartCache;

    @Mock
    private ExchangeRateChartCache exchangeRateChartCache;

    @InjectMocks
    private MarketIndexService marketIndexService;

    @Test
    @DisplayName("[분봉이 있으면 분봉을 그대로 반환한다]")
    void getIndexMinuteChart_minuteChartExists_returnsAsIs() {
        // given
        given(indexMinuteChartCache.get("KOSPI")).willReturn(
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
        given(indexMinuteChartCache.get("KOSPI")).willReturn(List.of());
        given(indexChartCache.get("KOSPI")).willReturn(List.of(
            new IndexChartResponse(LocalDate.of(2026, 7, 14), 7082.91, 7424.18, 7082.91, 7284.41)));

        // when
        List<IndexMinuteChartResponse> result = marketIndexService.getIndexMinuteChart("KOSPI");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).time()).isEqualTo(LocalDate.of(2026, 7, 14).atStartOfDay());
        assertThat(result.get(0).price()).isEqualTo(7284.41);
    }
}
