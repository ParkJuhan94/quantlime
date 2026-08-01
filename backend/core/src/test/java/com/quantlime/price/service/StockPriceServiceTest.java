package com.quantlime.price.service;

import com.quantlime.price.DomesticDailyPriceFixture;
import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.dto.response.CurrentPriceResponse;
import com.quantlime.price.dto.response.DailyChartResponse;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
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
    private DomesticDailyPriceService domesticDailyPriceService;

    @Mock
    private DomesticDailyPriceRepository domesticDailyPriceRepository;

    @Mock
    private OverseasDailyPriceRepository overseasDailyPriceRepository;

    @Mock
    private PriceCacheStore priceCacheStore;

    // 국내/해외 PreviousCloseCache가 같은 타입(PreviousCloseCache, 2026-08-01
    // 통합)이라 @InjectMocks의 타입 기반 매칭이 어느 쪽에 어느 Mock을
    // 넣을지 보장하지 못한다(실제로 시도해보니 둘 중 하나가 비어 NPE) -
    // 아래 @BeforeEach에서 생성자에 명시적으로 순서를 맞춰 넣는다.
    @Mock
    private PreviousCloseCache domesticPreviousCloseCache;

    @Mock
    private PreviousCloseCache overseasPreviousCloseCache;

    private StockPriceService stockPriceService;

    private final Stock stock = StockFixture.createStock();

    @BeforeEach
    void setUp() {
        stockPriceService = new StockPriceService(stockMasterService, domesticDailyPriceService, domesticDailyPriceRepository,
            overseasDailyPriceRepository, priceCacheStore, domesticPreviousCloseCache, overseasPreviousCloseCache);
    }

    @Test
    @DisplayName("[캐시에 스냅샷이 있으면 DB 폴백 없이 캐시 값을 반환한다]")
    void getCurrentPrice_cacheHit_returnsCachedResponseWithoutDbFallback() {
        // given
        String stockCode = stock.getStockCode();
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(priceCacheStore.find(stockCode)).willReturn(Optional.of(
            new PriceSnapshot(stockCode, 70000.0, 1.5, "2026-07-06T09:00:00+09:00")));

        // when
        CurrentPriceResponse response = stockPriceService.getCurrentPrice(stockCode);

        // then
        assertThat(response.price()).isEqualTo(70000.0);
        verify(domesticDailyPriceRepository, never()).findTopByStockCodeOrderByTradeDateDesc(stockCode);
    }

    @Test
    @DisplayName("[캐시 미스면 Toss를 호출하지 않고 DB의 마지막 종가로 응답한다]")
    void getCurrentPrice_cacheMiss_fallsBackToLastDbCloseWithoutCallingToss() {
        // given: DomesticMarketPriceSweepScheduler가 유일한 Toss 가격 조회원이어야 하는데,
        // 예전엔 이 캐시 미스 경로가 무페이싱으로 Toss를 직접 호출해 프론트의
        // 5초 동시 폴링(useStockPricesQuery)과 겹치며 429를 유발했음(2026-07-17) -
        // 이제는 DB에 있는 마지막 확정 종가만 반환하고 Toss는 절대 호출하지 않는다
        String stockCode = stock.getStockCode();
        DomesticDailyPrice latestClose = DomesticDailyPriceFixture.createDailyPrice(stockCode, LocalDate.of(2026, 7, 16));
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(priceCacheStore.find(stockCode)).willReturn(Optional.empty());
        given(domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode))
            .willReturn(Optional.of(latestClose));
        given(domesticPreviousCloseCache.get(List.of(stockCode)))
            .willReturn(Map.of(stockCode, 100.0));

        // when
        CurrentPriceResponse response = stockPriceService.getCurrentPrice(stockCode);

        // then: DomesticDailyPriceFixture의 종가는 105L 고정값(응답은 Double로 확대됨)
        assertThat(response.price()).isEqualTo(105.0);
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
        given(domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode))
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
        DomesticDailyPrice domesticDailyPrice = DomesticDailyPriceFixture.createDailyPrice(
            stockCode, LocalDate.now());
        given(domesticDailyPriceService.getDailyPrices(
            ArgumentMatchers.eq(stockCode), ArgumentMatchers.any(), ArgumentMatchers.any()))
            .willReturn(List.of(domesticDailyPrice));

        // when
        List<DailyChartResponse> result = stockPriceService.getChart(stockCode, 90);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).close()).isEqualTo(domesticDailyPrice.getClosePrice());
    }
}
