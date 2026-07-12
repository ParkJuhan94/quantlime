package com.quantlab.market.cache;

import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossExchangeRateResponse;
import com.quantlab.infra.toss.dto.TossExchangeRateResponse.ExchangeRateResult;
import com.quantlab.infra.upbit.UpbitApiClient;
import com.quantlab.infra.upbit.dto.UpbitTicker;
import com.quantlab.market.dto.response.MarketIndexResponse;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketIndexCacheTest {

    @Mock
    private TossApiClient tossApiClient;

    @Mock
    private UpbitApiClient upbitApiClient;

    @InjectMocks
    private MarketIndexCache marketIndexCache;

    @Test
    @DisplayName("[환율·비트코인 시세를 합쳐 응답을 만든다]")
    void get_firstCall_assemblesResponseFromBothSources() {
        // given
        given(tossApiClient.getExchangeRate("USD", "KRW")).willReturn(
            new TossExchangeRateResponse(new ExchangeRateResult("USD", "KRW", "1380.5", "UP")));
        given(upbitApiClient.getTicker("KRW-BTC")).willReturn(
            List.of(new UpbitTicker("KRW-BTC", 132000000L, 0.0345)));

        // when
        MarketIndexResponse result = marketIndexCache.get();

        // then
        assertThat(result.usdKrwRate()).isEqualTo(1380.5);
        assertThat(result.usdKrwChangeType()).isEqualTo("UP");
        assertThat(result.bitcoinPriceKrw()).isEqualTo(132000000L);
        assertThat(result.bitcoinChangeRate()).isEqualTo(3.45);
    }

    @Test
    @DisplayName("[TTL 이내 재조회는 외부 API를 다시 호출하지 않는다]")
    void get_withinTtl_doesNotRefetch() {
        // given
        given(tossApiClient.getExchangeRate("USD", "KRW")).willReturn(
            new TossExchangeRateResponse(new ExchangeRateResult("USD", "KRW", "1380.5", "UP")));
        given(upbitApiClient.getTicker("KRW-BTC")).willReturn(
            List.of(new UpbitTicker("KRW-BTC", 132000000L, 0.0345)));

        // when
        marketIndexCache.get();
        marketIndexCache.get();

        // then
        verify(tossApiClient, times(1)).getExchangeRate(anyString(), anyString());
        verify(upbitApiClient, times(1)).getTicker(anyString());
    }

    @Test
    @DisplayName("[TTL이 지나면 다시 조회한다]")
    void get_afterTtlExpired_refetches() {
        // given
        given(tossApiClient.getExchangeRate("USD", "KRW")).willReturn(
            new TossExchangeRateResponse(new ExchangeRateResult("USD", "KRW", "1380.5", "UP")));
        given(upbitApiClient.getTicker("KRW-BTC")).willReturn(
            List.of(new UpbitTicker("KRW-BTC", 132000000L, 0.0345)));
        marketIndexCache.get();

        // when: 마지막 갱신 시각을 TTL 밖으로 되돌려 만료 상태를 재현
        ReflectionTestUtils.setField(marketIndexCache, "cachedAt", Instant.now().minusSeconds(21));
        marketIndexCache.get();

        // then
        verify(tossApiClient, times(2)).getExchangeRate(anyString(), anyString());
    }
}
