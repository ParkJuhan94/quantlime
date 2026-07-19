package com.quantlab.market.cache;

import com.quantlab.infra.naver.NaverFinanceApiClient;
import com.quantlab.infra.naver.dto.NaverIndexCandleResponse;
import com.quantlab.market.dto.response.IndexChartResponse;
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
class WorldIndexChartCacheTest {

    @Mock
    private NaverFinanceApiClient naverFinanceApiClient;

    @InjectMocks
    private WorldIndexChartCache worldIndexChartCache;

    @Test
    @DisplayName("[타임존 오프셋이 붙은 localTradedAt에서 날짜만 추출해 오름차순 정렬한다]")
    void get_parsesOffsetDateTimeAndSortsByTradeDateAscending() {
        // given: 네이버 응답은 최신순(내림차순)으로 내려온다
        given(naverFinanceApiClient.getWorldIndexPrices(".IXIC", 60)).willReturn(List.of(
            new NaverIndexCandleResponse("2026-07-15T17:15:59-04:00", "26,201.58", "26,015.49", "26,300.00", "25,900.00"),
            new NaverIndexCandleResponse("2026-07-14T17:15:59-04:00", "25,873.18", "26,088.31", "26,139.37", "25,822.10")));

        // when
        List<IndexChartResponse> result = worldIndexChartCache.get(".IXIC", false);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).tradeDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(result.get(1).tradeDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(result.get(1).close()).isEqualTo(26201.58);
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 같은 로이터 코드에 대해 외부 API를 다시 호출하지 않는다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(naverFinanceApiClient.getWorldIndexPrices(".IXIC", 60)).willReturn(List.of(
            new NaverIndexCandleResponse("2026-07-15T17:15:59-04:00", "26,201.58", "26,015.49", "26,300.00", "25,900.00")));

        // when
        worldIndexChartCache.get(".IXIC", false);
        worldIndexChartCache.get(".IXIC", false);

        // then
        verify(naverFinanceApiClient, times(1)).getWorldIndexPrices(".IXIC", 60);
    }

    @Test
    @DisplayName("[로이터 코드가 다르면 서로 독립적으로 캐싱된다]")
    void get_differentCodes_cachedIndependently() {
        // given
        given(naverFinanceApiClient.getWorldIndexPrices(".IXIC", 60)).willReturn(List.of(
            new NaverIndexCandleResponse("2026-07-15T17:15:59-04:00", "26,201.58", "26,015.49", "26,300.00", "25,900.00")));
        given(naverFinanceApiClient.getWorldStockPrices("SOXX.O", 60)).willReturn(List.of(
            new NaverIndexCandleResponse("2026-07-15T16:00:00-04:00", "555.27", "575.12", "575.88", "538.53")));

        // when
        List<IndexChartResponse> nasdaq = worldIndexChartCache.get(".IXIC", false);
        List<IndexChartResponse> soxx = worldIndexChartCache.get("SOXX.O", true);

        // then
        assertThat(nasdaq.get(0).close()).isEqualTo(26201.58);
        assertThat(soxx.get(0).close()).isEqualTo(555.27);
    }

    @Test
    @DisplayName("[ETF는 지수 대신 종목 가격 조회 API를 사용한다]")
    void get_etfCode_usesWorldStockPricesInsteadOfIndex() {
        // given
        given(naverFinanceApiClient.getWorldStockPrices("SOXX.O", 60)).willReturn(List.of(
            new NaverIndexCandleResponse("2026-07-15T16:00:00-04:00", "555.27", "575.12", "575.88", "538.53")));

        // when
        worldIndexChartCache.get("SOXX.O", true);

        // then
        verify(naverFinanceApiClient, times(1)).getWorldStockPrices("SOXX.O", 60);
        verify(naverFinanceApiClient, times(0)).getWorldIndexPrices("SOXX.O", 60);
    }

    @Test
    @DisplayName("[TTL이 지나면 다시 조회한다]")
    void get_afterTtlExpired_refetches() {
        // given
        given(naverFinanceApiClient.getWorldIndexPrices(".IXIC", 60)).willReturn(List.of(
            new NaverIndexCandleResponse("2026-07-15T17:15:59-04:00", "26,201.58", "26,015.49", "26,300.00", "25,900.00")));
        worldIndexChartCache.get(".IXIC", false);

        // when: 캐시 맵을 비워 만료 상태를 재현
        @SuppressWarnings("unchecked")
        Map<String, Object> cacheByCode =
            (Map<String, Object>) ReflectionTestUtils.getField(worldIndexChartCache, "cacheByCode");
        cacheByCode.clear();
        worldIndexChartCache.get(".IXIC", false);

        // then
        verify(naverFinanceApiClient, times(2)).getWorldIndexPrices(".IXIC", 60);
    }
}
