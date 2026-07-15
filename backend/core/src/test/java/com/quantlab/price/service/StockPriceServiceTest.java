package com.quantlab.price.service;

import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.price.DailyPriceFixture;
import com.quantlab.price.cache.PriceCacheStore;
import com.quantlab.price.domain.DailyPrice;
import com.quantlab.price.dto.response.CurrentPriceResponse;
import com.quantlab.price.dto.response.DailyChartResponse;
import com.quantlab.price.dto.response.PriceSnapshot;
import com.quantlab.stock.StockFixture;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockPriceServiceTest {

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private TossApiClient tossApiClient;

    @Mock
    private DailyPriceService dailyPriceService;

    @Mock
    private PriceCacheStore priceCacheStore;

    @InjectMocks
    private StockPriceService stockPriceService;

    private final Stock stock = StockFixture.createStock();

    @Test
    @DisplayName("[캐시에 스냅샷이 있으면 Toss를 호출하지 않고 캐시 값을 반환한다]")
    void getCurrentPrice_cacheHit_returnsCachedResponseWithoutCallingToss() {
        // given
        String stockCode = stock.getStockCode();
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(priceCacheStore.find(stockCode)).willReturn(Optional.of(
            new PriceSnapshot(stockCode, 70000L, 1.5, "2026-07-06T09:00:00+09:00")));

        // when
        CurrentPriceResponse response = stockPriceService.getCurrentPrice(stockCode);

        // then
        assertThat(response.price()).isEqualTo(70000L);
        verify(tossApiClient, never()).getCurrentPrices(stockCode);
    }

    @Test
    @DisplayName("[캐시 미스면 Toss를 직접 호출해 정상 매핑된 응답을 반환한다]")
    void getCurrentPrice_cacheMiss_callsTossAndReturnsMappedResponse() {
        // given
        String stockCode = stock.getStockCode();
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(priceCacheStore.find(stockCode)).willReturn(Optional.empty());
        TossPriceResponse.TossPrice tossPrice = new TossPriceResponse.TossPrice(
            stockCode, "2026-07-06T09:00:00+09:00", "70000", "KRW");
        given(tossApiClient.getCurrentPrices(stockCode))
            .willReturn(new TossPriceResponse(List.of(tossPrice)));

        // when
        CurrentPriceResponse response = stockPriceService.getCurrentPrice(stockCode);

        // then
        assertThat(response.price()).isEqualTo(70000L);
        assertThat(response.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("[캐시 미스이고 토스 응답의 result도 비어있으면 price=null 응답을 반환한다]")
    void getCurrentPrice_emptyResult_returnsNullPrice() {
        // given
        String stockCode = stock.getStockCode();
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(priceCacheStore.find(stockCode)).willReturn(Optional.empty());
        given(tossApiClient.getCurrentPrices(stockCode))
            .willReturn(new TossPriceResponse(List.of()));

        // when
        CurrentPriceResponse response = stockPriceService.getCurrentPrice(stockCode);

        // then
        assertThat(response.stockCode()).isEqualTo(stockCode);
        assertThat(response.price()).isNull();
    }

    @Test
    @DisplayName("[차트 조회 시 종목 검증 후 일별 시세를 매핑해 반환한다]")
    void getChart_returnsMappedChartList() {
        // given
        String stockCode = stock.getStockCode();
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        DailyPrice dailyPrice = DailyPriceFixture.createDailyPrice(
            stockCode, LocalDate.now());
        given(dailyPriceService.getDailyPrices(
            ArgumentMatchers.eq(stockCode), ArgumentMatchers.any(), ArgumentMatchers.any()))
            .willReturn(List.of(dailyPrice));

        // when
        List<DailyChartResponse> result = stockPriceService.getChart(stockCode, 90);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).close()).isEqualTo(dailyPrice.getClosePrice());
    }
}
