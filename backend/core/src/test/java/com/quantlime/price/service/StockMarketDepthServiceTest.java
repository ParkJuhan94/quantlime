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

    @Test
    @DisplayName("[체결 내역은 가격·거래량을 숫자로 바꾸고, 응답 자체가 null이면 빈 목록이다]")
    void getTrades_parsesValuesAndHandlesNullResponse() {
        // given
        given(stockMarketDepthCache.trades(CODE))
            .willReturn(new TossTradeResponse(List.of(new TossTradeResponse.TossTrade("72000", "15", "ts", "KRW"))))
            .willReturn(null);

        // when & then
        assertThat(service.getTrades(CODE)).singleElement().satisfies(trade -> {
            assertThat(trade.price()).isEqualTo(72000.0);
            assertThat(trade.volume()).isEqualTo(15.0);
            assertThat(trade.currency()).isEqualTo("KRW");
        });
        assertThat(service.getTrades(CODE)).isEmpty();
    }

    @Test
    @DisplayName("[호가의 매도/매수 목록이 null이면 빈 목록으로 내려준다]")
    void getOrderbook_nullSides_becomeEmptyLists() {
        // given
        given(stockMarketDepthCache.orderbook(CODE))
            .willReturn(new TossOrderbookResponse.Orderbook("ts", "KRW", null, null));

        // when
        OrderbookResponse response = service.getOrderbook(CODE);

        // then
        assertThat(response.asks()).isEmpty();
        assertThat(response.bids()).isEmpty();
    }

    @Test
    @DisplayName("[상/하한가가 있으면 숫자로 변환한다]")
    void getPriceLimit_parsesLimits() {
        // given
        given(stockMarketDepthCache.priceLimit(CODE))
            .willReturn(new TossPriceLimitResponse.PriceLimit("t", "93600", "50400", "KRW"));

        // when
        PriceLimitResponse response = service.getPriceLimit(CODE);

        // then
        assertThat(response.upperLimitPrice()).isEqualTo(93600.0);
        assertThat(response.lowerLimitPrice()).isEqualTo(50400.0);
        assertThat(response.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("[알려진 유의사항 코드는 모두 한글 라벨로 바꾸고, 코드가 null이면 빈 라벨이다]")
    void getWarnings_mapsEveryKnownLabel() {
        // given
        List<String> types = List.of("LIQUIDATION_TRADING", "OVERHEATED", "INVESTMENT_WARNING", "INVESTMENT_RISK",
            "VI_STATIC_AND_DYNAMIC", "VI_STATIC", "VI_DYNAMIC", "STOCK_WARRANTS");
        List<TossStockWarningResponse.TossStockWarning> warnings = new java.util.ArrayList<>(types.stream()
            .map(type -> new TossStockWarningResponse.TossStockWarning(type, "KRX", "2026-10-01", "2026-10-02"))
            .toList());
        warnings.add(new TossStockWarningResponse.TossStockWarning(null, null, null, null));
        given(stockMarketDepthCache.warnings(CODE)).willReturn(new TossStockWarningResponse(warnings));

        // when
        List<StockWarningResponse> result = service.getWarnings(CODE);

        // then
        assertThat(result).extracting(StockWarningResponse::label).containsExactly(
            "정리매매", "단기과열종목", "투자경고종목", "투자위험종목",
            "VI 발동(정적+동적)", "VI 발동(정적)", "VI 발동(동적)", "신주인수권", "");
        assertThat(result.get(0).exchange()).isEqualTo("KRX");
    }

    @Test
    @DisplayName("[유의사항 응답이 null이거나 결과가 null이면 빈 목록이다]")
    void getWarnings_nullResponseOrResult_returnsEmpty() {
        // given
        given(stockMarketDepthCache.warnings(CODE)).willReturn(null).willReturn(new TossStockWarningResponse(null));

        // when & then
        assertThat(service.getWarnings(CODE)).isEmpty();
        assertThat(service.getWarnings(CODE)).isEmpty();
    }
}
