package com.quantlab.price.cache;

import com.quantlab.watchlist.repository.WatchlistRepository;
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
class WatchlistedStockCodeCacheTest {

    @Mock
    private WatchlistRepository watchlistRepository;

    @InjectMocks
    private WatchlistedStockCodeCache watchlistedStockCodeCache;

    @Test
    @DisplayName("[첫 조회 시 DB를 조회해 캐싱한다]")
    void get_firstCall_fetchesFromRepository() {
        // given
        given(watchlistRepository.findDistinctStockCodes()).willReturn(List.of("005930"));

        // when
        List<String> result = watchlistedStockCodeCache.get();

        // then
        assertThat(result).containsExactly("005930");
        verify(watchlistRepository, times(1)).findDistinctStockCodes();
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 DB를 다시 조회하지 않고 캐시를 반환한다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(watchlistRepository.findDistinctStockCodes()).willReturn(List.of("005930"));

        // when: 짧은 시간 내 두 번 호출
        watchlistedStockCodeCache.get();
        watchlistedStockCodeCache.get();

        // then: DB 조회는 최초 1회만
        verify(watchlistRepository, times(1)).findDistinctStockCodes();
    }

    @Test
    @DisplayName("[TTL이 지나면 다시 DB를 조회한다]")
    void get_afterTtlExpired_refetches() {
        // given
        given(watchlistRepository.findDistinctStockCodes())
            .willReturn(List.of("005930"))
            .willReturn(List.of("005930", "000660"));
        watchlistedStockCodeCache.get();

        // when: 마지막 갱신 시각을 TTL 밖으로 되돌려 만료 상태를 재현
        ReflectionTestUtils.setField(
            watchlistedStockCodeCache, "lastRefreshedAt", Instant.now().minusSeconds(31));
        List<String> result = watchlistedStockCodeCache.get();

        // then
        assertThat(result).containsExactly("005930", "000660");
        verify(watchlistRepository, times(2)).findDistinctStockCodes();
    }
}
