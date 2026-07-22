package com.quantlime.market.cache;

import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
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
class AllListedStockCacheTest {

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private AllListedStockCache allListedStockCache;

    @Test
    @DisplayName("[첫 조회 시 DB를 조회해 캐싱한다]")
    void get_firstCall_fetchesFromRepository() {
        // given
        Stock stock = StockFixture.createStock();
        given(stockRepository.findByListingStatus(ListingStatus.LISTED)).willReturn(List.of(stock));

        // when
        List<Stock> result = allListedStockCache.get();

        // then
        assertThat(result).containsExactly(stock);
        verify(stockRepository, times(1)).findByListingStatus(ListingStatus.LISTED);
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 DB를 다시 조회하지 않고 캐시를 반환한다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(stockRepository.findByListingStatus(ListingStatus.LISTED))
            .willReturn(List.of(StockFixture.createStock()));

        // when
        allListedStockCache.get();
        allListedStockCache.get();

        // then
        verify(stockRepository, times(1)).findByListingStatus(ListingStatus.LISTED);
    }

    @Test
    @DisplayName("[TTL이 지나면 다시 DB를 조회한다]")
    void get_afterTtlExpired_refetches() {
        // given
        given(stockRepository.findByListingStatus(ListingStatus.LISTED))
            .willReturn(List.of(StockFixture.createStock("005930", "삼성전자")))
            .willReturn(List.of(
                StockFixture.createStock("005930", "삼성전자"),
                StockFixture.createStock("000660", "SK하이닉스")));
        allListedStockCache.get();

        // when: 마지막 갱신 시각을 TTL 밖으로 되돌려 만료 상태를 재현
        ReflectionTestUtils.setField(
            allListedStockCache, "lastRefreshedAt", Instant.now().minusSeconds(601));
        List<Stock> result = allListedStockCache.get();

        // then
        assertThat(result).hasSize(2);
        verify(stockRepository, times(2)).findByListingStatus(ListingStatus.LISTED);
    }
}
