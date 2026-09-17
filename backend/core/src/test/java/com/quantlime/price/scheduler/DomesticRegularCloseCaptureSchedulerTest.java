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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
        given(priceCacheStore.findAll(anyList())).willReturn(Map.of(
            stockCode, new PriceSnapshot(stockCode, 71200.0, 1.2, "2026-08-17T15:30:00+09:00")));
        given(domesticRegularClosePriceRepository.findStockCodeByTradeDate(any())).willReturn(List.of());

        // when
        scheduler.captureRegularClose();

        // then: 소수점 시세는 반올림해 Long으로 저장(국내는 원 단위 정수), 신규 행만
        // 모아 saveAll 한 번으로 저장한다(2026-09 성능 감사)
        ArgumentCaptor<List<DomesticRegularClosePrice>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(domesticRegularClosePriceRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).hasSize(1);
        DomesticRegularClosePrice saved = savedCaptor.getValue().get(0);
        assertThat(saved.getStockCode()).isEqualTo(stockCode);
        assertThat(saved.getTradeDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getClosePrice()).isEqualTo(71200L);
    }

    @Test
    @DisplayName("[Redis 시세 스냅샷이 없는 종목은 저장을 스킵한다]")
    void captureRegularClose_noSnapshot_skipsStock() {
        // given
        given(domesticMarketCalendarCache.isTradingDayToday()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(priceCacheStore.findAll(anyList())).willReturn(Map.of());
        given(domesticRegularClosePriceRepository.findStockCodeByTradeDate(any())).willReturn(List.of());

        // when
        scheduler.captureRegularClose();

        // then
        verify(domesticRegularClosePriceRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("[이미 오늘 캡처된 종목은 다시 저장하지 않는다(멱등)]")
    void captureRegularClose_alreadyCapturedToday_skipsDuplicateSave() {
        // given
        String stockCode = stock.getStockCode();
        given(domesticMarketCalendarCache.isTradingDayToday()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(priceCacheStore.findAll(anyList())).willReturn(Map.of(
            stockCode, new PriceSnapshot(stockCode, 71200.0, 1.2, "2026-08-17T15:30:00+09:00")));
        given(domesticRegularClosePriceRepository.findStockCodeByTradeDate(any())).willReturn(List.of(stockCode));

        // when
        scheduler.captureRegularClose();

        // then
        verify(domesticRegularClosePriceRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("[캡처 중 예외가 나도 스케줄러 호출 자체는 예외를 전파하지 않는다]")
    void captureRegularClose_calendarThrows_doesNotPropagate() {
        // given
        given(domesticMarketCalendarCache.isTradingDayToday()).willThrow(new RuntimeException("boom"));

        // when / then: SafeExecutor로 감싸 예외가 밖으로 새지 않아야 한다
        scheduler.captureRegularClose();
    }

    @Test
    @DisplayName("[기동 캐치업: 15:30~15:35 안전 시간대 안이면 정상 캡처한다]")
    void captureIfWithinStartupSafeWindow_withinWindow_capturesNormally() {
        // given
        String stockCode = stock.getStockCode();
        given(domesticMarketCalendarCache.isTradingDayToday()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(priceCacheStore.findAll(anyList())).willReturn(Map.of(
            stockCode, new PriceSnapshot(stockCode, 71200.0, 1.2, "2026-08-17T15:32:00+09:00")));
        given(domesticRegularClosePriceRepository.findStockCodeByTradeDate(any())).willReturn(List.of());

        // when: 15:32는 안전 시간대(15:30~15:35) 안
        scheduler.captureIfWithinStartupSafeWindow(LocalTime.of(15, 32));

        // then
        verify(domesticRegularClosePriceRepository).saveAll(any());
    }

    @Test
    @DisplayName("[기동 캐치업: 안전 시간대를 넘긴 뒤(NXT 애프터마켓 중)엔 캡처를 스킵한다]")
    void captureIfWithinStartupSafeWindow_afterWindow_skipsCaptureEvenIfSnapshotExists() {
        // when: 19:47처럼 늦게 재기동하는 상황 - 이 시점 Redis 값은 이미 NXT
        // 애프터마켓 드리프트가 꼈을 수 있어(회귀 대상) 아예 조회하지 않아야 한다.
        scheduler.captureIfWithinStartupSafeWindow(LocalTime.of(19, 47));

        // then
        verify(domesticMarketCalendarCache, never()).isTradingDayToday();
        verify(priceCacheStore, never()).findAll(any());
        verify(domesticRegularClosePriceRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("[기동 캐치업: 15:30 이전(장중)에는 캡처를 스킵한다]")
    void captureIfWithinStartupSafeWindow_beforeWindow_skipsCapture() {
        // when
        scheduler.captureIfWithinStartupSafeWindow(LocalTime.of(11, 0));

        // then
        verify(domesticMarketCalendarCache, never()).isTradingDayToday();
        verify(domesticRegularClosePriceRepository, never()).saveAll(any());
    }
}
