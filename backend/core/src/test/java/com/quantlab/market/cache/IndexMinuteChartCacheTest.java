package com.quantlab.market.cache;

import com.quantlab.infra.naver.NaverFinanceApiClient;
import com.quantlab.infra.naver.dto.NaverIndexMinuteCandleResponse;
import com.quantlab.market.dto.response.IndexMinuteChartResponse;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class IndexMinuteChartCacheTest {

    @Mock
    private NaverFinanceApiClient naverFinanceApiClient;

    @InjectMocks
    private IndexMinuteChartCache indexMinuteChartCache;

    @Test
    @DisplayName("[yyyyMMddHHmmss를 파싱하고 시각 오름차순으로 정렬한다]")
    void get_parsesAndSortsByTimeAscending() {
        // given: 네이버 응답은 최신순(내림차순)으로 내려올 수 있다
        given(naverFinanceApiClient.getIndexMinuteCandles("KOSPI")).willReturn(List.of(
            new NaverIndexMinuteCandleResponse("20260715090100", 7099.17),
            new NaverIndexMinuteCandleResponse("20260715090000", 7095.79)));

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
        given(naverFinanceApiClient.getIndexMinuteCandles("KOSPI")).willReturn(List.of(
            new NaverIndexMinuteCandleResponse("20260715090000", 7095.79)));

        // when
        indexMinuteChartCache.get("KOSPI");
        indexMinuteChartCache.get("KOSPI");

        // then
        verify(naverFinanceApiClient, times(1)).getIndexMinuteCandles("KOSPI");
    }

    @Test
    @DisplayName("[지수 코드가 다르면 서로 독립적으로 캐싱된다]")
    void get_differentCodes_cachedIndependently() {
        // given
        given(naverFinanceApiClient.getIndexMinuteCandles("KOSPI")).willReturn(List.of(
            new NaverIndexMinuteCandleResponse("20260715090000", 7095.79)));
        given(naverFinanceApiClient.getIndexMinuteCandles("KOSDAQ")).willReturn(List.of(
            new NaverIndexMinuteCandleResponse("20260715090000", 829.43)));

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
        given(naverFinanceApiClient.getIndexMinuteCandles("KOSPI")).willReturn(List.of(
            new NaverIndexMinuteCandleResponse("20260715090000", 7095.79)));
        indexMinuteChartCache.get("KOSPI");

        // when: 캐시 맵을 비워 만료 상태를 재현
        @SuppressWarnings("unchecked")
        Map<String, Object> cacheByCode =
            (Map<String, Object>) ReflectionTestUtils.getField(indexMinuteChartCache, "cacheByCode");
        cacheByCode.clear();
        indexMinuteChartCache.get("KOSPI");

        // then
        verify(naverFinanceApiClient, times(2)).getIndexMinuteCandles("KOSPI");
    }
}
