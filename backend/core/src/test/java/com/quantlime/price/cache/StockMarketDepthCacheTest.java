package com.quantlime.price.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossOrderbookResponse;
import com.quantlime.infra.toss.dto.TossPriceLimitResponse;
import com.quantlime.infra.toss.dto.TossStockWarningResponse;
import com.quantlime.infra.toss.dto.TossTradeResponse;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import java.time.Duration;
import java.time.Instant;
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

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockMarketDepthCacheTest {

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private StockMarketDepthCache cache;

    private TossOrderbookResponse orderbookResponse() {
        return new TossOrderbookResponse(new TossOrderbookResponse.Orderbook(
            null, "KRW",
            List.of(new TossOrderbookResponse.OrderbookEntry("72100", "8500")),
            List.of(new TossOrderbookResponse.OrderbookEntry("72000", "100"))));
    }

    @Test
    @DisplayName("[TTL 안에서 같은 종목을 다시 조회하면 토스를 한 번만 호출한다]")
    void orderbook_withinTtl_callsTossOnce() {
        // given
        given(tossApiClient.getOrderbook("005930")).willReturn(orderbookResponse());

        // when
        cache.orderbook("005930");
        cache.orderbook("005930");

        // then
        verify(tossApiClient, times(1)).getOrderbook("005930");
    }

    @Test
    @DisplayName("[종목별로 캐시가 분리된다]")
    void orderbook_differentSymbols_areCachedSeparately() {
        // given
        given(tossApiClient.getOrderbook("005930")).willReturn(orderbookResponse());
        given(tossApiClient.getOrderbook("000660")).willReturn(orderbookResponse());

        // when
        cache.orderbook("005930");
        cache.orderbook("000660");

        // then
        verify(tossApiClient, times(1)).getOrderbook("005930");
        verify(tossApiClient, times(1)).getOrderbook("000660");
    }

    @Test
    @DisplayName("[이전 캐시가 없는데 토스 조회가 실패하면 예외를 그대로 전파한다]")
    void orderbook_failureWithoutStale_propagates() {
        // given
        given(tossApiClient.getOrderbook("005930"))
            .willThrow(new ExternalApiException(TossApiErrorCode.ORDERBOOK_INQUIRY_FAILED));

        // when & then
        assertThatThrownBy(() -> cache.orderbook("005930")).isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("[토스 호출 결과를 그대로 변환 없이 돌려준다]")
    void orderbook_returnsTossResult() {
        // given
        given(tossApiClient.getOrderbook("005930")).willReturn(orderbookResponse());

        // when
        TossOrderbookResponse.Orderbook result = cache.orderbook("005930");

        // then
        assertThat(result.asks()).hasSize(1);
        assertThat(result.bids().get(0).price()).isEqualTo("72000");
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> internalCache() {
        return (Map<Object, Object>) ReflectionTestUtils.getField(cache, "cache");
    }

    // 캐시 항목의 적재 시각만 과거로 되돌려 TTL 만료를 재현한다(Entry는 private record라 리플렉션으로 재생성)
    private void expireAllEntries() throws Exception {
        for (Map.Entry<Object, Object> entry : internalCache().entrySet()) {
            Object old = entry.getValue();
            var constructor = old.getClass().getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object value = ReflectionTestUtils.invokeMethod(old, "value");
            entry.setValue(constructor.newInstance(value, Instant.now().minus(Duration.ofHours(1))));
        }
    }

    @Test
    @DisplayName("[체결·상하한가·유의사항도 TTL 안에서는 토스를 한 번만 호출한다]")
    void otherKinds_withinTtl_callTossOnce() {
        given(tossApiClient.getTrades("005930", 30)).willReturn(new TossTradeResponse(List.of()));
        given(tossApiClient.getPriceLimits("005930"))
            .willReturn(new TossPriceLimitResponse(new TossPriceLimitResponse.PriceLimit("t", "1", "2", "KRW")));
        given(tossApiClient.getStockWarnings("005930")).willReturn(new TossStockWarningResponse(List.of()));

        for (int i = 0; i < 2; i++) {
            cache.trades("005930");
            cache.priceLimit("005930");
            cache.warnings("005930");
        }

        verify(tossApiClient, times(1)).getTrades("005930", 30);
        verify(tossApiClient, times(1)).getPriceLimits("005930");
        verify(tossApiClient, times(1)).getStockWarnings("005930");
    }

    @Test
    @DisplayName("[TTL이 지난 뒤 토스 조회가 실패하면 만료된 이전 값을 그대로 돌려준다(stale-serve)]")
    void orderbook_refreshFails_servesExpiredPreviousValue() throws Exception {
        TossOrderbookResponse response = orderbookResponse();
        given(tossApiClient.getOrderbook("005930")).willReturn(response).willThrow(new IllegalStateException("toss down"));
        TossOrderbookResponse.Orderbook first = cache.orderbook("005930");
        expireAllEntries();

        TossOrderbookResponse.Orderbook second = cache.orderbook("005930");

        assertThat(second).isSameAs(first);
        verify(tossApiClient, times(2)).getOrderbook("005930");
    }

    @Test
    @DisplayName("[항목이 4000개에 도달하면 만료분을 정리하고, 그래도 넘으면 전부 비운 뒤 새 항목을 채운다]")
    void load_overCapacity_evictsExpiredThenClears() {
        given(tossApiClient.getStockWarnings(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new TossStockWarningResponse(List.of()));
        for (int i = 0; i < 4000; i++) {
            cache.warnings("S" + i);
        }
        assertThat(internalCache()).hasSize(4000);

        // 4001번째 적재 시점에 모두 신선하므로 만료 정리로는 줄지 않아 전부 비워진 뒤 새 항목만 남는다
        cache.warnings("NEW");

        assertThat(internalCache()).hasSize(1);
    }
}
