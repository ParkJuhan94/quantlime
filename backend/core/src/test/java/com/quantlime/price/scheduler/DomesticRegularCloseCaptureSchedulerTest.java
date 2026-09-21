package com.quantlime.price.scheduler;

import com.quantlime.market.cache.DomesticListedStockCache;
import com.quantlime.price.cache.DomesticMarketCalendarCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
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
    private DomesticDailyPriceRepository domesticDailyPriceRepository;

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
    @DisplayName("[오늘 행이 없는 종목은 정규장 종가만 담은 임시 행을 신규 저장한다]")
    void captureRegularClose_noRowToday_insertsRegularCloseOnlyRow() {
        // given
        String stockCode = stock.getStockCode();
        given(domesticMarketCalendarCache.isTradingDayToday()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(priceCacheStore.findAll(anyList())).willReturn(Map.of(
            stockCode, new PriceSnapshot(stockCode, 71200.0, 1.2, "2026-08-17T15:35:00+09:00")));
        given(domesticDailyPriceRepository.findByStockCodeInAndTradeDate(anyList(), any()))
            .willReturn(List.of());

        // when
        scheduler.captureRegularClose();

        // then: 소수점 시세는 반올림해 Long으로 저장(국내는 원 단위 정수), O/H/L=종가·
        // 거래량=0인 임시값 - 뒤이은 일봉 배치가 실제 O/H/L/V로 채운다.
        ArgumentCaptor<List<DomesticDailyPrice>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(domesticDailyPriceRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).hasSize(1);
        DomesticDailyPrice saved = savedCaptor.getValue().get(0);
        assertThat(saved.getStockCode()).isEqualTo(stockCode);
        assertThat(saved.getTradeDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getClosePrice()).isEqualTo(71200L);
        assertThat(saved.getOpenPrice()).isEqualTo(71200L);
        assertThat(saved.getVolume()).isEqualTo(0L);
        assertThat(saved.isRegularCloseConfirmed()).isTrue();
    }

    @Test
    @DisplayName("[오늘 행이 이미 있지만 미확정이면 종가만 확정 업데이트한다]")
    void captureRegularClose_rowExistsButNotConfirmed_updatesCloseOnly() {
        // given: 장중 갭필 등으로 오늘 행이 먼저 만들어져 있는 일반적인 상황(NXT
        // 포함 임시 종가) - 정규장 캡처는 close만 확정하고 O/H/L/V는 건드리지 않는다.
        String stockCode = stock.getStockCode();
        DomesticDailyPrice existing = DomesticDailyPrice.of(
            stockCode, LocalDate.now(), 70000L, 71500L, 69500L, 71000L, 1000L);
        given(domesticMarketCalendarCache.isTradingDayToday()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(priceCacheStore.findAll(anyList())).willReturn(Map.of(
            stockCode, new PriceSnapshot(stockCode, 71200.0, 1.2, "2026-08-17T15:35:00+09:00")));
        given(domesticDailyPriceRepository.findByStockCodeInAndTradeDate(anyList(), any()))
            .willReturn(List.of(existing));

        // when
        scheduler.captureRegularClose();

        // then
        ArgumentCaptor<List<DomesticDailyPrice>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(domesticDailyPriceRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).containsExactly(existing);
        assertThat(existing.getClosePrice()).isEqualTo(71200L);
        assertThat(existing.getOpenPrice()).isEqualTo(70000L); // O/H/L/V는 그대로
        assertThat(existing.isRegularCloseConfirmed()).isTrue();
    }

    @Test
    @DisplayName("[Redis 시세 스냅샷이 없는 종목은 저장을 스킵한다]")
    void captureRegularClose_noSnapshot_skipsStock() {
        // given
        given(domesticMarketCalendarCache.isTradingDayToday()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(priceCacheStore.findAll(anyList())).willReturn(Map.of());
        given(domesticDailyPriceRepository.findByStockCodeInAndTradeDate(anyList(), any()))
            .willReturn(List.of());

        // when
        scheduler.captureRegularClose();

        // then
        verify(domesticDailyPriceRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("[이미 오늘 정규장 종가가 확정된 종목은 다시 저장하지 않는다(멱등)]")
    void captureRegularClose_alreadyConfirmedToday_skipsDuplicateSave() {
        // given
        String stockCode = stock.getStockCode();
        DomesticDailyPrice alreadyConfirmed = DomesticDailyPrice.of(
            stockCode, LocalDate.now(), 71200L, 71200L, 71200L, 71200L, 0L);
        alreadyConfirmed.confirmRegularClose(71200L);
        given(domesticMarketCalendarCache.isTradingDayToday()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(priceCacheStore.findAll(anyList())).willReturn(Map.of(
            stockCode, new PriceSnapshot(stockCode, 71200.0, 1.2, "2026-08-17T15:35:00+09:00")));
        given(domesticDailyPriceRepository.findByStockCodeInAndTradeDate(anyList(), any()))
            .willReturn(List.of(alreadyConfirmed));

        // when
        scheduler.captureRegularClose();

        // then
        verify(domesticDailyPriceRepository, never()).saveAll(any());
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
    @DisplayName("[기동 캐치업: 15:35~15:39 안전 시간대 안이면 정상 캡처한다]")
    void captureIfWithinStartupSafeWindow_withinWindow_capturesNormally() {
        // given
        String stockCode = stock.getStockCode();
        given(domesticMarketCalendarCache.isTradingDayToday()).willReturn(true);
        given(domesticListedStockCache.get()).willReturn(List.of(stock));
        given(priceCacheStore.findAll(anyList())).willReturn(Map.of(
            stockCode, new PriceSnapshot(stockCode, 71200.0, 1.2, "2026-08-17T15:37:00+09:00")));
        given(domesticDailyPriceRepository.findByStockCodeInAndTradeDate(anyList(), any()))
            .willReturn(List.of());

        // when: 15:37은 안전 시간대(15:35~15:39) 안
        scheduler.captureIfWithinStartupSafeWindow(LocalTime.of(15, 37));

        // then
        verify(domesticDailyPriceRepository).saveAll(any());
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
        verify(domesticDailyPriceRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("[기동 캐치업: 15:35 이전(장중)에는 캡처를 스킵한다]")
    void captureIfWithinStartupSafeWindow_beforeWindow_skipsCapture() {
        // when
        scheduler.captureIfWithinStartupSafeWindow(LocalTime.of(11, 0));

        // then
        verify(domesticMarketCalendarCache, never()).isTradingDayToday();
        verify(domesticDailyPriceRepository, never()).saveAll(any());
    }
}
