package com.quantlab.stock.cache;

import com.quantlab.stock.StockFixture;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.dto.response.StockDetailResponse;
import com.quantlab.watchlist.service.WatchlistService;
import java.time.Instant;
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
class PopularStocksCacheTest {

    @Mock
    private WatchlistService watchlistService;

    @InjectMocks
    private PopularStocksCache popularStocksCache;

    @Test
    @DisplayName("[요청한 limit만큼만 잘라 반환한다]")
    void get_returnsOnlyRequestedLimit() {
        // given
        Stock stock1 = StockFixture.createStock("005930", "삼성전자");
        Stock stock2 = StockFixture.createStock("000660", "SK하이닉스");
        given(watchlistService.getPopularStocks(20)).willReturn(List.of(stock1, stock2));

        // when
        List<StockDetailResponse> result = popularStocksCache.get(1);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stockCode()).isEqualTo("005930");
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 외부(DB) 조회를 다시 하지 않는다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(watchlistService.getPopularStocks(20)).willReturn(
            List.of(StockFixture.createStock("005930", "삼성전자")));

        // when
        popularStocksCache.get(5);
        popularStocksCache.get(5);

        // then
        verify(watchlistService, times(1)).getPopularStocks(20);
    }

    @Test
    @DisplayName("[TTL이 지나면 다시 조회한다]")
    void get_afterTtlExpired_refetches() {
        // given
        given(watchlistService.getPopularStocks(20)).willReturn(
            List.of(StockFixture.createStock("005930", "삼성전자")));
        popularStocksCache.get(5);

        // when: 마지막 갱신 시각을 TTL 밖으로 되돌려 만료 상태를 재현
        ReflectionTestUtils.setField(popularStocksCache, "cachedAt", Instant.now().minusSeconds(301));
        popularStocksCache.get(5);

        // then
        verify(watchlistService, times(2)).getPopularStocks(20);
    }
}
