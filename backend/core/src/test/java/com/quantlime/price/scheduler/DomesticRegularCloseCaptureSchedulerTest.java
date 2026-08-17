package com.quantlime.price.scheduler;

import com.quantlime.market.cache.DomesticListedStockCache;
import com.quantlime.price.cache.DomesticMarketCalendarCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.domain.DomesticRegularClosePrice;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.repository.DomesticRegularClosePriceRepository;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DomesticRegularCloseCaptureSchedulerTest {

    @Mock
    private DomesticMarketCalendarCache domesticMarketCalendarCache;

    @Mock
    private DomesticListedStockCache domesticListedStockCache;

    @Mock
    private PriceCacheStore priceCacheStore;

    @Mock
    private DomesticRegularClosePriceRepository domesticRegularClosePriceRepository;

    @InjectMocks
    private DomesticRegularCloseCaptureScheduler scheduler;

    private final Stock stock = StockFixture.createStock();

    @Test
    @DisplayName("[휴장일이면 전종목 조회 없이 캡처를 스킵한다]")
    void captureRegularClose_holiday_skipsWithoutQueryingStocks() {
        // given
        given(domesticMarketCalendarCache.isTradingDayToday()).willReturn(false);

        // when
        scheduler.captureRegularClose();

        // then
        verify(domesticListedStockCache, never()).get();
    }

    @Test
    @DisplayName("[영업일이면 Redis 시세 스냅샷을 정규장 종가로 저장한다]")
    void captureRegularClose_tradingDay_savesRedisSnapshotAsRegularClose() {
        // given
        String stockCode = stock.getStockCode();
        given(domesticMarketCalendarCache.isTradingDayToday()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(priceCacheStore.find(stockCode)).willReturn(
            Optional.of(new PriceSnapshot(stockCode, 71200.0, 1.2, "2026-08-17T15:30:00+09:00")));
        given(domesticRegularClosePriceRepository.existsByStockCodeAndTradeDate(eq(stockCode), any()))
            .willReturn(false);

        // when
        scheduler.captureRegularClose();

        // then: 소수점 시세는 반올림해 Long으로 저장(국내는 원 단위 정수)
        verify(domesticRegularClosePriceRepository).save(
            org.mockito.ArgumentMatchers.argThat((DomesticRegularClosePrice saved) ->
                saved.getStockCode().equals(stockCode)
                    && saved.getTradeDate().equals(LocalDate.now())
                    && saved.getClosePrice() == 71200L));
    }

    @Test
    @DisplayName("[Redis 시세 스냅샷이 없는 종목은 저장을 스킵한다]")
    void captureRegularClose_noSnapshot_skipsStock() {
        // given
        String stockCode = stock.getStockCode();
        given(domesticMarketCalendarCache.isTradingDayToday()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(priceCacheStore.find(stockCode)).willReturn(Optional.empty());

        // when
        scheduler.captureRegularClose();

        // then
        verify(domesticRegularClosePriceRepository, never()).save(any());
    }

    @Test
    @DisplayName("[이미 오늘 캡처된 종목은 다시 저장하지 않는다(멱등)]")
    void captureRegularClose_alreadyCapturedToday_skipsDuplicateSave() {
        // given
        String stockCode = stock.getStockCode();
        given(domesticMarketCalendarCache.isTradingDayToday()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(priceCacheStore.find(stockCode)).willReturn(
            Optional.of(new PriceSnapshot(stockCode, 71200.0, 1.2, "2026-08-17T15:30:00+09:00")));
        given(domesticRegularClosePriceRepository.existsByStockCodeAndTradeDate(eq(stockCode), any()))
            .willReturn(true);

        // when
        scheduler.captureRegularClose();

        // then
        verify(domesticRegularClosePriceRepository, never()).save(any());
    }

    @Test
    @DisplayName("[캡처 중 예외가 나도 스케줄러 호출 자체는 예외를 전파하지 않는다]")
    void captureRegularClose_calendarThrows_doesNotPropagate() {
        // given
        given(domesticMarketCalendarCache.isTradingDayToday()).willThrow(new RuntimeException("boom"));

        // when / then: SafeExecutor로 감싸 예외가 밖으로 새지 않아야 한다
        scheduler.captureRegularClose();
    }
}
