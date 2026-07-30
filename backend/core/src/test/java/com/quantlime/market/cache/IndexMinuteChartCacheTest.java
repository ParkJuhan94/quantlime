package com.quantlime.market.cache;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossMarketIndicatorCandleResponse;
import com.quantlime.market.dto.response.IndexMinuteChartResponse;
import java.time.LocalDateTime;
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
 * 네이버 -> Toss market-indicators/candles(interval=1m)로 이관(2026-07-29)
 * 이후의 회귀 테스트.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class IndexMinuteChartCacheTest {

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private IndexMinuteChartCache indexMinuteChartCache;

    @Test
    @DisplayName("[시각 오름차순으로 정렬한다]")
    void get_parsesAndSortsByTimeAscending() {
        // given: Toss 응답은 최신순(내림차순)으로 내려올 수 있다
        given(tossApiClient.getMarketIndicatorCandles("KOSPI", "1m", 200, null))
            .willReturn(candleResponse(
                candle("2026-07-15T09:01:00+09:00", "7099.17"),
                candle("2026-07-15T09:00:00+09:00", "7095.79")));

        // when
        List<IndexMinuteChartResponse> result = indexMinuteChartCache.get("KOSPI");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).time()).isEqualTo(LocalDateTime.of(2026, 7, 15, 9, 0, 0));
        assertThat(result.get(1).time()).isEqualTo(LocalDateTime.of(2026, 7, 15, 9, 1, 0));
        assertThat(result.get(1).price()).isEqualTo(7099.17);
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 같은 지수코드에 대해 외부 API를 다시 호출하지 않는다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(tossApiClient.getMarketIndicatorCandles("KOSPI", "1m", 200, null))
            .willReturn(candleResponse(candle("2026-07-15T09:00:00+09:00", "7095.79")));

        // when
        indexMinuteChartCache.get("KOSPI");
        indexMinuteChartCache.get("KOSPI");

        // then
        verify(tossApiClient, times(1)).getMarketIndicatorCandles("KOSPI", "1m", 200, null);
    }

    @Test
    @DisplayName("[지수 코드가 다르면 서로 독립적으로 캐싱된다]")
    void get_differentCodes_cachedIndependently() {
        // given
        given(tossApiClient.getMarketIndicatorCandles("KOSPI", "1m", 200, null))
            .willReturn(candleResponse(candle("2026-07-15T09:00:00+09:00", "7095.79")));
        given(tossApiClient.getMarketIndicatorCandles("KOSDAQ", "1m", 200, null))
            .willReturn(candleResponse(candle("2026-07-15T09:00:00+09:00", "829.43")));

        // when
        List<IndexMinuteChartResponse> kospi = indexMinuteChartCache.get("KOSPI");
        List<IndexMinuteChartResponse> kosdaq = indexMinuteChartCache.get("KOSDAQ");

        // then
        assertThat(kospi.get(0).price()).isEqualTo(7095.79);
        assertThat(kosdaq.get(0).price()).isEqualTo(829.43);
    }

    @Test
    @DisplayName("[TTL이 지나면 다시 조회한다]")
    void get_afterTtlExpired_refetches() {
        // given
        given(tossApiClient.getMarketIndicatorCandles(any(), any(), anyInt(), any()))
            .willReturn(candleResponse(candle("2026-07-15T09:00:00+09:00", "7095.79")));
        indexMinuteChartCache.get("KOSPI");

        // when: 캐시 맵을 비워 만료 상태를 재현
        @SuppressWarnings("unchecked")
        Map<String, Object> cacheByCode =
            (Map<String, Object>) ReflectionTestUtils.getField(indexMinuteChartCache, "cacheByCode");
        cacheByCode.clear();
        indexMinuteChartCache.get("KOSPI");

        // then
        verify(tossApiClient, times(2)).getMarketIndicatorCandles("KOSPI", "1m", 200, null);
    }

    private TossMarketIndicatorCandleResponse.MarketIndicatorCandle candle(String timestamp, String price) {
        return new TossMarketIndicatorCandleResponse.MarketIndicatorCandle(timestamp, price, price, price, price, "0");
    }

    private TossMarketIndicatorCandleResponse candleResponse(
        TossMarketIndicatorCandleResponse.MarketIndicatorCandle... candles) {
        return new TossMarketIndicatorCandleResponse(
            new TossMarketIndicatorCandleResponse.MarketIndicatorCandlePageResult(List.of(candles), null));
    }
}
