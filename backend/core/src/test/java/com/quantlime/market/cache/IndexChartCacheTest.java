package com.quantlime.market.cache;

import com.quantlime.infra.naver.NaverFinanceApiClient;
import com.quantlime.infra.naver.dto.NaverIndexCandleResponse;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class IndexChartCacheTest {

    @Mock
    private NaverFinanceApiClient naverFinanceApiClient;

    @InjectMocks
    private IndexChartCache indexChartCache;

    @Test
    @DisplayName("[콤마를 제거해 파싱하고 거래일 오름차순으로 정렬한다]")
    void get_parsesAndSortsByTradeDateAscending() {
        // given: 네이버 응답은 최신순(내림차순)으로 내려온다
        given(naverFinanceApiClient.getIndexPrices("KOSPI", 60)).willReturn(List.of(
            new NaverIndexCandleResponse("2026-07-15", "7,284.41", "7,082.91", "7,424.18", "7,082.91"),
            new NaverIndexCandleResponse("2026-07-14", "6,856.83", "6,769.06", "6,979.92", "6,448.86")));

        // when
        List<IndexChartResponse> result = indexChartCache.get("KOSPI");

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
        given(naverFinanceApiClient.getIndexPrices("KOSPI", 60)).willReturn(List.of(
            new NaverIndexCandleResponse("2026-07-15", "7,284.41", "7,082.91", "7,424.18", "7,082.91")));

        // when
        indexChartCache.get("KOSPI");
        indexChartCache.get("KOSPI");

        // then
        verify(naverFinanceApiClient, times(1)).getIndexPrices("KOSPI", 60);
    }

    @Test
    @DisplayName("[지수 코드가 다르면 서로 독립적으로 캐싱된다]")
    void get_differentCodes_cachedIndependently() {
        // given
        given(naverFinanceApiClient.getIndexPrices("KOSPI", 60)).willReturn(List.of(
            new NaverIndexCandleResponse("2026-07-15", "7,284.41", "7,082.91", "7,424.18", "7,082.91")));
        given(naverFinanceApiClient.getIndexPrices("KOSDAQ", 60)).willReturn(List.of(
            new NaverIndexCandleResponse("2026-07-15", "829.43", "800.00", "830.00", "795.00")));

        // when
        List<IndexChartResponse> kospi = indexChartCache.get("KOSPI");
        List<IndexChartResponse> kosdaq = indexChartCache.get("KOSDAQ");

        // then
        assertThat(kospi.get(0).close()).isEqualTo(7284.41);
        assertThat(kosdaq.get(0).close()).isEqualTo(829.43);
    }

    @Test
    @DisplayName("[TTL이 지나면 다시 조회한다]")
    void get_afterTtlExpired_refetches() {
        // given
        given(naverFinanceApiClient.getIndexPrices("KOSPI", 60)).willReturn(List.of(
            new NaverIndexCandleResponse("2026-07-15", "7,284.41", "7,082.91", "7,424.18", "7,082.91")));
        indexChartCache.get("KOSPI");

        // when: 캐시 맵에 저장된 항목의 cachedAt을 TTL 밖으로 되돌려 만료 상태를 재현
        @SuppressWarnings("unchecked")
        Map<String, Object> cacheByCode =
            (Map<String, Object>) ReflectionTestUtils.getField(indexChartCache, "cacheByCode");
        cacheByCode.clear();
        indexChartCache.get("KOSPI");

        // then
        verify(naverFinanceApiClient, times(2)).getIndexPrices("KOSPI", 60);
    }
}
