package com.quantlime.price.cache;

import com.quantlime.stock.domain.MarketType;
import com.quantlime.watchlist.repository.WatchlistRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 생성자가 시장 범위(List&lt;MarketType&gt;)를 받는 구조라(2026-08-01
 * 국내/해외 통합) @InjectMocks로 자동 주입할 수 없어 {@link #setUp}에서
 * 직접 생성한다 - 국내(domesticValues())로 고정해도 캐싱 제어 흐름
 * 검증에는 문제없다(해외 쪽 동작은 PriceCacheConfig의 Bean 등록만 다르고
 * 이 클래스 자체 로직은 시장 무관).
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class WatchlistedStockCodeCacheTest {

    @Mock
    private WatchlistRepository watchlistRepository;

    private WatchlistedStockCodeCache watchlistedStockCodeCache;

    @BeforeEach
    void setUp() {
        watchlistedStockCodeCache = new WatchlistedStockCodeCache(watchlistRepository, MarketType.domesticValues());
    }

    @Test
    @DisplayName("[첫 조회 시 DB를 조회해 캐싱한다]")
    void get_firstCall_fetchesFromRepository() {
        // given
        given(watchlistRepository.findDistinctStockCodesByMarketTypeIn(MarketType.domesticValues()))
            .willReturn(List.of("005930"));

        // when
        List<String> result = watchlistedStockCodeCache.get();

        // then
        assertThat(result).containsExactly("005930");
        verify(watchlistRepository, times(1)).findDistinctStockCodesByMarketTypeIn(MarketType.domesticValues());
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 DB를 다시 조회하지 않고 캐시를 반환한다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(watchlistRepository.findDistinctStockCodesByMarketTypeIn(MarketType.domesticValues()))
            .willReturn(List.of("005930"));

        // when: 짧은 시간 내 두 번 호출
        watchlistedStockCodeCache.get();
        watchlistedStockCodeCache.get();

        // then: DB 조회는 최초 1회만
        verify(watchlistRepository, times(1)).findDistinctStockCodesByMarketTypeIn(MarketType.domesticValues());
    }

    @Test
    @DisplayName("[TTL이 지나면 다시 DB를 조회한다]")
    void get_afterTtlExpired_refetches() {
        // given
        given(watchlistRepository.findDistinctStockCodesByMarketTypeIn(MarketType.domesticValues()))
            .willReturn(List.of("005930"))
            .willReturn(List.of("005930", "000660"));
        watchlistedStockCodeCache.get();

        // when: 마지막 갱신 시각을 TTL 밖으로 되돌려 만료 상태를 재현
        ReflectionTestUtils.setField(
            watchlistedStockCodeCache, "lastRefreshedAt", Instant.now().minusSeconds(31));
        List<String> result = watchlistedStockCodeCache.get();

        // then
        assertThat(result).containsExactly("005930", "000660");
        verify(watchlistRepository, times(2)).findDistinctStockCodesByMarketTypeIn(MarketType.domesticValues());
    }
}
