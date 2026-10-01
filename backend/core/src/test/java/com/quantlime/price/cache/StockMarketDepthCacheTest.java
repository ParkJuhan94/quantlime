package com.quantlime.price.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossOrderbookResponse;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
