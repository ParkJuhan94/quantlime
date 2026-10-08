package com.quantlime.stock.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.stock.dto.response.StockFundamentalsResponse;
import com.quantlime.stock.implement.StockFundamentalsCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockFundamentalsCacheTest {

    @Mock
    private StockFundamentalsCollector stockFundamentalsCollector;

    @InjectMocks
    private StockFundamentalsCache stockFundamentalsCache;

    @Test
    @DisplayName("[TTL 이내 재조회는 수집을 다시 호출하지 않는다]")
    void get_withinTtl_doesNotRecollect() {
        // given
        StockFundamentalsResponse response = new StockFundamentalsResponse(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
        given(stockFundamentalsCollector.collect("005930")).willReturn(response);

        // when
        StockFundamentalsResponse first = stockFundamentalsCache.get("005930");
        StockFundamentalsResponse second = stockFundamentalsCache.get("005930");

        // then
        assertThat(first).isSameAs(response);
        assertThat(second).isSameAs(response);
        verify(stockFundamentalsCollector, times(1)).collect("005930");
    }
}
