package com.quantlime.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.quantlime.infra.toss.dto.TossOrderbookResponse;
import com.quantlime.infra.toss.dto.TossPriceLimitResponse;
import com.quantlime.infra.toss.dto.TossStockWarningResponse;
import com.quantlime.infra.toss.dto.TossTradeResponse;
import com.quantlime.price.cache.StockMarketDepthCache;
import com.quantlime.price.dto.response.OrderbookResponse;
import com.quantlime.price.dto.response.PriceLimitResponse;
import com.quantlime.price.dto.response.StockWarningResponse;
import com.quantlime.price.dto.response.TradeResponse;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.service.StockMasterService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockMarketDepthServiceTest {

    private static final String CODE = "005930";

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private StockMarketDepthCache stockMarketDepthCache;

    @InjectMocks
    private StockMarketDepthService service;

    @BeforeEach
    void setUp() {
        given(stockMasterService.getStockByCode(CODE)).willReturn(StockFixture.createStock(CODE, "삼성전자"));
    }

    @Test
    @DisplayName("[호가 문자열 가격/잔량을 숫자로 변환한다]")
    void getOrderbook_parsesDecimalStrings() {
        // given
        given(stockMarketDepthCache.orderbook(CODE)).willReturn(new TossOrderbookResponse.Orderbook(
            "2026-03-25T09:30:00+09:00", "KRW",
            List.of(new TossOrderbookResponse.OrderbookEntry("72100", "8500")),
            List.of(new TossOrderbookResponse.OrderbookEntry("72000", "100"))));

        // when
        OrderbookResponse response = service.getOrderbook(CODE);

        // then
        assertThat(response.asks().get(0).price()).isEqualTo(72100.0);
        assertThat(response.bids().get(0).volume()).isEqualTo(100.0);
        assertThat(response.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("[체결 결과가 비어 있으면 빈 목록을 반환한다]")
    void getTrades_nullResult_returnsEmpty() {
        // given
        given(stockMarketDepthCache.trades(CODE)).willReturn(new TossTradeResponse(null));

        // when
        List<TradeResponse> result = service.getTrades(CODE);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("[가격제한이 없는 시장은 상/하한가를 null로 내려준다]")
    void getPriceLimit_nullLimits_returnsNulls() {
        // given
        given(stockMarketDepthCache.priceLimit(CODE))
            .willReturn(new TossPriceLimitResponse.PriceLimit("t", null, null, "USD"));

        // when
        PriceLimitResponse response = service.getPriceLimit(CODE);

        // then
        assertThat(response.upperLimitPrice()).isNull();
        assertThat(response.lowerLimitPrice()).isNull();
    }

    @Test
    @DisplayName("[유의사항은 한글 라벨로 변환하고 모르는 코드는 원문을 유지한다]")
    void getWarnings_mapsKnownLabelsAndKeepsUnknown() {
        // given
        given(stockMarketDepthCache.warnings(CODE)).willReturn(new TossStockWarningResponse(List.of(
            new TossStockWarningResponse.TossStockWarning("VI_STATIC", "KRX", null, null),
            new TossStockWarningResponse.TossStockWarning("NEW_FUTURE_CODE", null, null, null))));

        // when
        List<StockWarningResponse> result = service.getWarnings(CODE);

        // then
        assertThat(result).extracting(StockWarningResponse::label)
            .containsExactly("VI 발동(정적)", "NEW_FUTURE_CODE");
    }
}
