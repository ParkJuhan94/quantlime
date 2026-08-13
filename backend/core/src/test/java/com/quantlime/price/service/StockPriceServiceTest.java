package com.quantlime.price.service;

import com.quantlime.price.DomesticDailyPriceFixture;
import com.quantlime.price.OverseasDailyPriceFixture;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
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

    private StockPriceService stockPriceService;

    private final Stock stock = StockFixture.createStock();

    @BeforeEach
    void setUp() {
        stockPriceService = new StockPriceService(stockMasterService, domesticDailyPriceService, domesticDailyPriceRepository,
            overseasDailyPriceRepository, priceCacheStore);
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
        // 이제는 DB에 있는 마지막 확정 종가만 반환하고 Toss는 절대 호출하지 않는다.
        //
        // 전일종가 조회는 실제 오늘 날짜가 아니라 latestClose의 거래일 기준으로
        // 이뤄져야 한다(주말/공휴일 등 캐시 미스가 나는 시점의 실제 오늘 날짜로
        // 조회하면 latestClose 자기 자신과 같은 행이 잡혀 등락률이 항상 0%로
        // 계산되는 버그가 있었다) - findLatestBeforeDate를 latestClose.getTradeDate()로
        // 호출하는지가 이 테스트의 핵심이라 전일종가 종가값을 latestClose와
        // 다르게(100L) 잡아 구분한다.
        String stockCode = stock.getStockCode();
        LocalDate latestTradeDate = LocalDate.of(2026, 7, 16);
        DomesticDailyPrice latestClose = DomesticDailyPriceFixture.createDailyPrice(stockCode, latestTradeDate);
        DomesticDailyPrice previousClose = DomesticDailyPrice.of(
            stockCode, latestTradeDate.minusDays(1), 90L, 105L, 85L, 100L, 900L);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(priceCacheStore.find(stockCode)).willReturn(Optional.empty());
        given(domesticDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode))
            .willReturn(Optional.of(latestClose));
        given(domesticDailyPriceRepository.findLatestBeforeDate(List.of(stockCode), latestTradeDate))
            .willReturn(List.of(previousClose));

        // when
        CurrentPriceResponse response = stockPriceService.getCurrentPrice(stockCode);

        // then: DomesticDailyPriceFixture의 종가는 105L 고정값(응답은 Double로 확대됨)
        assertThat(response.price()).isEqualTo(105.0);
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.changeRate()).isCloseTo(5.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("[해외 종목도 캐시 미스면 DB의 마지막 종가 거래일 기준으로 전일종가를 조회한다]")
    void getCurrentPrice_overseasCacheMiss_usesLatestCloseTradeDateForPreviousClose() {
        // given
        Stock overseasStock = StockFixture.createOverseasStock("AAPL", "Apple");
        String stockCode = overseasStock.getStockCode();
        LocalDate latestTradeDate = LocalDate.of(2026, 7, 16);
        OverseasDailyPrice latestClose = OverseasDailyPriceFixture.createDailyPrice(stockCode, latestTradeDate);
        OverseasDailyPrice previousClose = OverseasDailyPrice.of(
            stockCode, latestTradeDate.minusDays(1), 90.0, 105.0, 85.0, 100.0, 900L);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(overseasStock);
        given(priceCacheStore.find(stockCode)).willReturn(Optional.empty());
        given(overseasDailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc(stockCode))
            .willReturn(Optional.of(latestClose));
        given(overseasDailyPriceRepository.findLatestBeforeDate(List.of(stockCode), latestTradeDate))
            .willReturn(List.of(previousClose));

        // when
        CurrentPriceResponse response = stockPriceService.getCurrentPrice(stockCode);

        // then: OverseasDailyPriceFixture의 종가는 105.5 고정값
        assertThat(response.price()).isEqualTo(105.5);
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.changeRate()).isCloseTo(5.5, org.assertj.core.data.Offset.offset(0.001));
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
        assertThat(result.get(0).close()).isEqualTo(domesticDailyPrice.getClosePrice().doubleValue());
    }

    @Test
    @DisplayName("[해외 종목 차트 조회 시 해외 일별 시세 리포지토리를 조회한다]")
    void getChart_overseasStock_queriesOverseasRepositoryInsteadOfDomestic() {
        // given: 시장 구분 없이 항상 국내 테이블만 조회하던 버그(해외 종목은
        // domestic_daily_price에 행이 없어 차트가 항상 빈 배열로 응답됨)의 회귀 테스트.
        Stock overseasStock = StockFixture.createOverseasStock("AAPL", "Apple");
        String stockCode = overseasStock.getStockCode();
        given(stockMasterService.getStockByCode(stockCode)).willReturn(overseasStock);
        OverseasDailyPrice overseasDailyPrice = OverseasDailyPriceFixture.createDailyPrice(
            stockCode, LocalDate.now());
        given(overseasDailyPriceRepository.findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
            ArgumentMatchers.eq(stockCode), ArgumentMatchers.any(), ArgumentMatchers.any()))
            .willReturn(List.of(overseasDailyPrice));

        // when
        List<DailyChartResponse> result = stockPriceService.getChart(stockCode, 90);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).close()).isEqualTo(overseasDailyPrice.getClosePrice());
        verify(domesticDailyPriceService, never()).getDailyPrices(
            ArgumentMatchers.eq(stockCode), ArgumentMatchers.any(), ArgumentMatchers.any());
    }
}
