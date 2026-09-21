package com.quantlime.price.service;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.market.cache.DomesticListedStockCache;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.dto.RegularCloseBackfillResult;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.stock.StockFixture;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RegularCloseBackfillServiceTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private DomesticDailyPriceRepository domesticDailyPriceRepository;

    @Mock
    private DomesticListedStockCache domesticListedStockCache;

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private RegularCloseBackfillService regularCloseBackfillService;

    @Test
    @DisplayName("[미확정 행은 1분봉 조회 결과로 정규장 종가를 확정한다]")
    void backfill_unconfirmedRow_confirmsRegularCloseFromMinuteCandle() {
        // given
        LocalDate tradeDate = LocalDate.now().minusDays(1);
        DomesticDailyPrice unconfirmed = DomesticDailyPrice.of(
            STOCK_CODE, tradeDate, 70000L, 71000L, 69000L, 70800L, 1_000_000L);
        given(domesticDailyPriceRepository.findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
            eq(STOCK_CODE), any(), any())).willReturn(List.of(unconfirmed));
        // before는 KST 15:30을 UTC(Z 표기)로 변환해 넘긴다 - TossApiClient
        // .get1MinuteCandleBefore는 +09:00처럼 +가 든 값을 있는 그대로도, 미리
        // %2B로 인코딩해도 정상 전달하지 못한다(실측 확인, 그 클래스 javadoc
        // 참고) - 이 구체적 문자열 매칭이 Z 표기 변환을 지킨다.
        String expectedBeforeUtc = tradeDate.atTime(LocalTime.of(15, 30))
            .atZone(ZoneId.of("Asia/Seoul")).toInstant().toString();
        given(tossApiClient.get1MinuteCandleBefore(STOCK_CODE, expectedBeforeUtc))
            .willReturn(minuteCandlePage("70500"));

        // when
        RegularCloseBackfillResult result = regularCloseBackfillService.backfill(List.of(STOCK_CODE), 20);

        // then
        assertThat(result).isEqualTo(RegularCloseBackfillResult.of(1, 0, 0));
        verify(domesticDailyPriceRepository).save(unconfirmed);
        assertThat(unconfirmed.getClosePrice()).isEqualTo(70500L);
        assertThat(unconfirmed.isRegularCloseConfirmed()).isTrue();
    }

    @Test
    @DisplayName("[이미 정규장 종가가 확정된 행은 API 호출 없이 건너뛴다]")
    void backfill_alreadyConfirmedRow_skipsWithoutApiCall() {
        // given
        LocalDate tradeDate = LocalDate.now().minusDays(1);
        DomesticDailyPrice confirmed = DomesticDailyPrice.of(
            STOCK_CODE, tradeDate, 70000L, 71000L, 69000L, 70500L, 1_000_000L);
        confirmed.confirmRegularClose(70500L);
        given(domesticDailyPriceRepository.findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
            eq(STOCK_CODE), any(), any())).willReturn(List.of(confirmed));

        // when
        RegularCloseBackfillResult result = regularCloseBackfillService.backfill(List.of(STOCK_CODE), 20);

        // then
        assertThat(result).isEqualTo(RegularCloseBackfillResult.of(0, 0, 0));
        verify(tossApiClient, never()).get1MinuteCandleBefore(anyString(), anyString());
        verify(domesticDailyPriceRepository, never()).save(any());
    }

    @Test
    @DisplayName("[1분봉이 없으면(거래정지 등) 스킵으로 집계하고 계속 진행한다]")
    void backfill_noMinuteCandle_countsAsSkippedAndContinues() {
        // given
        LocalDate tradeDate = LocalDate.now().minusDays(1);
        DomesticDailyPrice unconfirmed = DomesticDailyPrice.of(
            STOCK_CODE, tradeDate, 70000L, 71000L, 69000L, 70800L, 0L);
        given(domesticDailyPriceRepository.findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
            eq(STOCK_CODE), any(), any())).willReturn(List.of(unconfirmed));
        given(tossApiClient.get1MinuteCandleBefore(eq(STOCK_CODE), anyString()))
            .willReturn(new TossCandleResponse(new TossCandleResponse.TossCandlePageResult(List.of(), null)));

        // when
        RegularCloseBackfillResult result = regularCloseBackfillService.backfill(List.of(STOCK_CODE), 20);

        // then
        assertThat(result).isEqualTo(RegularCloseBackfillResult.of(0, 1, 0));
        verify(domesticDailyPriceRepository, never()).save(any());
    }

    @Test
    @DisplayName("[1분봉 조회가 예외를 던지면 실패로 집계하고 계속 진행한다]")
    void backfill_apiThrows_countsAsFailedAndContinues() {
        // given
        LocalDate tradeDate = LocalDate.now().minusDays(1);
        DomesticDailyPrice unconfirmed = DomesticDailyPrice.of(
            STOCK_CODE, tradeDate, 70000L, 71000L, 69000L, 70800L, 0L);
        given(domesticDailyPriceRepository.findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
            eq(STOCK_CODE), any(), any())).willReturn(List.of(unconfirmed));
        given(tossApiClient.get1MinuteCandleBefore(eq(STOCK_CODE), anyString()))
            .willThrow(new RuntimeException("boom"));

        // when
        RegularCloseBackfillResult result = regularCloseBackfillService.backfill(List.of(STOCK_CODE), 20);

        // then
        assertThat(result).isEqualTo(RegularCloseBackfillResult.of(0, 0, 1));
        verify(domesticDailyPriceRepository, never()).save(any());
    }

    @Test
    @DisplayName("[stockCodes를 지정하지 않으면 국내 상장종목 캐시 전체를 대상으로 한다]")
    void backfill_noStockCodesGiven_usesDomesticListedStockCache() {
        // given
        given(domesticListedStockCache.get()).willReturn(List.of(StockFixture.createStock()));
        given(domesticDailyPriceRepository.findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
            anyString(), any(), any())).willReturn(List.of());

        // when
        regularCloseBackfillService.backfill(null, 20);

        // then
        verify(domesticListedStockCache).get();
    }

    private TossCandleResponse minuteCandlePage(String closePrice) {
        TossCandleResponse.TossCandle candle = new TossCandleResponse.TossCandle(
            "2026-09-01T15:29:00+09:00", "70000", "70600", "69900", closePrice, "12000", "KRW");
        return new TossCandleResponse(new TossCandleResponse.TossCandlePageResult(List.of(candle), null));
    }
}
