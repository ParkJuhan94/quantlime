package com.quantlime.price.service;

import com.quantlime.price.DailyPriceFixture;
import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.dto.response.CurrentPriceResponse;
import com.quantlime.price.dto.response.DailyChartResponse;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.repository.DailyPriceRepository;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
    private DailyPriceService dailyPriceService;

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Mock
    private PriceCacheStore priceCacheStore;

    @Mock
    private PreviousCloseCache previousCloseCache;

    @InjectMocks
    private StockPriceService stockPriceService;

    private final Stock stock = StockFixture.createStock();

    @Test
    @DisplayName("[캐시에 스냅샷이 있으면 DB 폴백 없이 캐시 값을 반환한다]")
    void getCurrentPrice_cacheHit_returnsCachedResponseWithoutDbFallback() {
        // given
        String stockCode = stock.getStockCode();
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(priceCacheStore.find(stockCode)).willReturn(Optional.of(
            new PriceSnapshot(stockCode, 70000L, 1.5, "2026-07-06T09:00:00+09:00")));

        // when
        CurrentPriceResponse response = stockPriceService.getCurrentPrice(stockCode);

        // then
        assertThat(response.price()).isEqualTo(70000L);
        verify(dailyPriceRepository, never()).findTopByStockCodeOrderByTradeDateDesc(stockCode);
    }

    @Test
    @DisplayName("[캐시 미스면 Toss를 호출하지 않고 DB의 마지막 종가로 응답한다]")
    void getCurrentPrice_cacheMiss_fallsBackToLastDbCloseWithoutCallingToss() {
        // given: MarketPriceSweepScheduler가 유일한 Toss 가격 조회원이어야 하는데,
        // 예전엔 이 캐시 미스 경로가 무페이싱으로 Toss를 직접 호출해 프론트의
        // 5초 동시 폴링(useStockPricesQuery)과 겹치며 429를 유발했음(2026-07-17) -
        // 이제는 DB에 있는 마지막 확정 종가만 반환하고 Toss는 절대 호출하지 않는다
        String stockCode = stock.getStockCode();
        DailyPrice latestClose = DailyPriceFixture.createDailyPrice(stockCode, LocalDate.of(2026, 7, 16));
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(priceCacheStore.find(stockCode)).willReturn(Optional.empty());
        given(dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode))
            .willReturn(Optional.of(latestClose));
        given(previousCloseCache.get(List.of(stockCode)))
            .willReturn(Map.of(stockCode, 100L));

        // when
        CurrentPriceResponse response = stockPriceService.getCurrentPrice(stockCode);

        // then: DailyPriceFixture의 종가는 105L 고정값
        assertThat(response.price()).isEqualTo(105L);
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.changeRate()).isCloseTo(5.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("[캐시 미스이고 DB 이력도 없으면 price=null 응답을 반환한다]")
    void getCurrentPrice_cacheMissAndNoDbHistory_returnsNullPrice() {
        // given
        String stockCode = stock.getStockCode();
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(priceCacheStore.find(stockCode)).willReturn(Optional.empty());
        given(dailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode))
            .willReturn(Optional.empty());

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
