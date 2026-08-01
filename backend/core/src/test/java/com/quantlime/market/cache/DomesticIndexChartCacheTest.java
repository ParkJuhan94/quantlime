package com.quantlime.market.cache;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossMarketIndicatorCandleResponse;
import com.quantlime.market.dto.response.IndexChartResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 네이버 -> Toss market-indicators/candles로 이관(2026-07-29) 이후의
 * 회귀 테스트.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DomesticIndexChartCacheTest {

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private DomesticIndexChartCache domesticIndexChartCache;

    @Test
    @DisplayName("[거래일 오름차순으로 정렬한다]")
    void get_parsesAndSortsByTradeDateAscending() {
        // given: Toss 응답은 최신순(내림차순)으로 내려온다
        given(tossApiClient.getMarketIndicatorCandles("KOSPI", "1d", 200, null))
            .willReturn(candleResponse(
                candle("2026-07-15T00:00:00+09:00", "7,082.91", "7,424.18", "7,082.91", "7,284.41"),
                candle("2026-07-14T00:00:00+09:00", "6,769.06", "6,979.92", "6,448.86", "6,856.83")));

        // when
        List<IndexChartResponse> result = domesticIndexChartCache.get("KOSPI");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).tradeDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(result.get(1).tradeDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(result.get(1).close()).isEqualTo(7284.41);
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 같은 지수코드에 대해 외부 API를 다시 호출하지 않는다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(tossApiClient.getMarketIndicatorCandles("KOSPI", "1d", 200, null))
            .willReturn(candleResponse(
                candle("2026-07-15T00:00:00+09:00", "7,082.91", "7,424.18", "7,082.91", "7,284.41")));

        // when
        domesticIndexChartCache.get("KOSPI");
        domesticIndexChartCache.get("KOSPI");

        // then
        verify(tossApiClient, times(1)).getMarketIndicatorCandles("KOSPI", "1d", 200, null);
    }

    @Test
    @DisplayName("[지수 코드가 다르면 서로 독립적으로 캐싱된다]")
    void get_differentCodes_cachedIndependently() {
        // given
        given(tossApiClient.getMarketIndicatorCandles("KOSPI", "1d", 200, null))
            .willReturn(candleResponse(
                candle("2026-07-15T00:00:00+09:00", "7,082.91", "7,424.18", "7,082.91", "7,284.41")));
        given(tossApiClient.getMarketIndicatorCandles("KOSDAQ", "1d", 200, null))
            .willReturn(candleResponse(
                candle("2026-07-15T00:00:00+09:00", "800.00", "830.00", "795.00", "829.43")));

        // when
        List<IndexChartResponse> kospi = domesticIndexChartCache.get("KOSPI");
        List<IndexChartResponse> kosdaq = domesticIndexChartCache.get("KOSDAQ");

        // then
        assertThat(kospi.get(0).close()).isEqualTo(7284.41);
        assertThat(kosdaq.get(0).close()).isEqualTo(829.43);
    }

    @Test
    @DisplayName("[TTL이 지나면 다시 조회한다]")
    void get_afterTtlExpired_refetches() {
        // given
        given(tossApiClient.getMarketIndicatorCandles(any(), any(), anyInt(), any()))
            .willReturn(candleResponse(
                candle("2026-07-15T00:00:00+09:00", "7,082.91", "7,424.18", "7,082.91", "7,284.41")));
        domesticIndexChartCache.get("KOSPI");

        // when: 캐시 맵에 저장된 항목을 지워 만료 상태를 재현
        @SuppressWarnings("unchecked")
        Map<String, Object> cacheByCode =
            (Map<String, Object>) ReflectionTestUtils.getField(domesticIndexChartCache, "cacheByCode");
        cacheByCode.clear();
        domesticIndexChartCache.get("KOSPI");

        // then
        verify(tossApiClient, times(2)).getMarketIndicatorCandles("KOSPI", "1d", 200, null);
    }

    private TossMarketIndicatorCandleResponse.MarketIndicatorCandle candle(
        String timestamp, String open, String high, String low, String close) {
        return new TossMarketIndicatorCandleResponse.MarketIndicatorCandle(
            timestamp, open.replace(",", ""), high.replace(",", ""), low.replace(",", ""), close.replace(",", ""), "1000000");
    }

    private TossMarketIndicatorCandleResponse candleResponse(
        TossMarketIndicatorCandleResponse.MarketIndicatorCandle... candles) {
        return new TossMarketIndicatorCandleResponse(
            new TossMarketIndicatorCandleResponse.MarketIndicatorCandlePageResult(List.of(candles), null));
    }
}
