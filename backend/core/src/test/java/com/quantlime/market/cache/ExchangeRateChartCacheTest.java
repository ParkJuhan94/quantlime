package com.quantlime.market.cache;

import com.quantlime.infra.naver.NaverFinanceApiClient;
import com.quantlime.infra.naver.dto.NaverExchangeRateCandleResponse;
import com.quantlime.market.dto.response.IndexChartResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
class ExchangeRateChartCacheTest {

    @Mock
    private NaverFinanceApiClient naverFinanceApiClient;

    @InjectMocks
    private ExchangeRateChartCache exchangeRateChartCache;

    @Test
    @DisplayName("[콤마를 제거해 파싱하고 거래일 오름차순으로 정렬하며 OHLC 전부 종가로 채운다]")
    void get_parsesAndSortsByTradeDateAscending() {
        // given: 네이버 응답은 최신순(내림차순)으로 내려온다
        given(naverFinanceApiClient.getExchangeRatePrices("FX_USDKRW", 60)).willReturn(List.of(
            new NaverExchangeRateCandleResponse("2026-07-15", "1,487.50"),
            new NaverExchangeRateCandleResponse("2026-07-14", "1,491.00")));

        // when
        List<IndexChartResponse> result = exchangeRateChartCache.get();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).tradeDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(result.get(1).tradeDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(result.get(1).close()).isEqualTo(1487.50);
        assertThat(result.get(1).open()).isEqualTo(1487.50);
        assertThat(result.get(1).high()).isEqualTo(1487.50);
        assertThat(result.get(1).low()).isEqualTo(1487.50);
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 외부 API를 다시 호출하지 않는다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(naverFinanceApiClient.getExchangeRatePrices("FX_USDKRW", 60)).willReturn(List.of(
            new NaverExchangeRateCandleResponse("2026-07-15", "1,487.50")));

        // when
        exchangeRateChartCache.get();
        exchangeRateChartCache.get();

        // then
        verify(naverFinanceApiClient, times(1)).getExchangeRatePrices("FX_USDKRW", 60);
    }

    @Test
    @DisplayName("[TTL이 지나면 다시 조회한다]")
    void get_afterTtlExpired_refetches() {
        // given
        given(naverFinanceApiClient.getExchangeRatePrices("FX_USDKRW", 60)).willReturn(List.of(
            new NaverExchangeRateCandleResponse("2026-07-15", "1,487.50")));
        exchangeRateChartCache.get();

        // when: 마지막 갱신 시각을 TTL 밖으로 되돌려 만료 상태를 재현
        ReflectionTestUtils.setField(exchangeRateChartCache, "cachedAt", Instant.now().minusSeconds(61));
        exchangeRateChartCache.get();

        // then
        verify(naverFinanceApiClient, times(2)).getExchangeRatePrices("FX_USDKRW", 60);
    }
}
