package com.quantlime.market.cache;

import com.quantlime.infra.upbit.UpbitApiClient;
import com.quantlime.infra.upbit.dto.UpbitMinuteCandle;
import com.quantlime.market.dto.response.IndexMinuteChartResponse;
import java.time.Instant;
import java.time.LocalDateTime;
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
class BitcoinChartCacheTest {

    @Mock
    private UpbitApiClient upbitApiClient;

    @InjectMocks
    private BitcoinChartCache bitcoinChartCache;

    @Test
    @DisplayName("[30분봉 48개를 조회해 시각 오름차순으로 정렬한다]")
    void get_fetchesThirtyMinuteCandlesAndSortsAscending() {
        // given: Upbit 응답은 최신순(내림차순)으로 내려온다
        given(upbitApiClient.getMinuteCandles("KRW-BTC", 30, 48)).willReturn(List.of(
            new UpbitMinuteCandle("2026-07-15T22:30:00", 96201000.0),
            new UpbitMinuteCandle("2026-07-15T22:00:00", 95959000.0)));

        // when
        List<IndexMinuteChartResponse> result = bitcoinChartCache.get();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).time()).isEqualTo(LocalDateTime.of(2026, 7, 15, 22, 0, 0));
        assertThat(result.get(1).time()).isEqualTo(LocalDateTime.of(2026, 7, 15, 22, 30, 0));
        assertThat(result.get(1).price()).isEqualTo(96201000.0);
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 외부 API를 다시 호출하지 않는다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(upbitApiClient.getMinuteCandles("KRW-BTC", 30, 48)).willReturn(List.of(
            new UpbitMinuteCandle("2026-07-15T22:00:00", 95959000.0)));

        // when
        bitcoinChartCache.get();
        bitcoinChartCache.get();

        // then
        verify(upbitApiClient, times(1)).getMinuteCandles("KRW-BTC", 30, 48);
    }

    @Test
    @DisplayName("[TTL이 지나면 다시 조회한다]")
    void get_afterTtlExpired_refetches() {
        // given
        given(upbitApiClient.getMinuteCandles("KRW-BTC", 30, 48)).willReturn(List.of(
            new UpbitMinuteCandle("2026-07-15T22:00:00", 95959000.0)));
        bitcoinChartCache.get();

        // when: 마지막 갱신 시각을 TTL 밖으로 되돌려 만료 상태를 재현
        ReflectionTestUtils.setField(bitcoinChartCache, "cachedAt", Instant.now().minusSeconds(21));
        bitcoinChartCache.get();

        // then
        verify(upbitApiClient, times(2)).getMinuteCandles("KRW-BTC", 30, 48);
    }
}
