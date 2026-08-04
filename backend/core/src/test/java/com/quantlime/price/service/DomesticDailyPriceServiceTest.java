package com.quantlime.price.service;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DomesticDailyPriceServiceTest {

    private static final String STOCK_CODE = "005930";
    // refreshRecent(stockCode)의 기본 lookback(10)이 DailyPriceSettlementPolicy.
    // MIN_LOOKBACK_CANDLES(20)로 상향되므로, 실제 조회 count는 항상 20이다.
    private static final int MIN_LOOKBACK_CANDLES = 20;

    @Mock
    private DomesticDailyPriceRepository domesticDailyPriceRepository;

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private DomesticDailyPriceService domesticDailyPriceService;

    @Test
    @DisplayName("[재확정 윈도우 밖의 과거 거래일이 밀려있어도 조회한 기간 내 빠진 날짜를 전부 채운다]")
    void refreshRecent_missingRecentDays_savesOnlyNewOnes() {
        // given: 재확정 윈도우(20일) 밖의 과거 3일치를 조회했는데 그중 1일치는 이미 저장돼 있음
        LocalDate start = LocalDate.now().minusDays(25);
        TossCandleResponse page = candlePage(3, start, null);
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(MIN_LOOKBACK_CANDLES), any())).willReturn(page);
        given(domesticDailyPriceRepository.existsByStockCodeAndTradeDate(eq(STOCK_CODE), eq(start)))
            .willReturn(false);
        given(domesticDailyPriceRepository.existsByStockCodeAndTradeDate(eq(STOCK_CODE), eq(start.minusDays(1))))
            .willReturn(false);
        given(domesticDailyPriceRepository.existsByStockCodeAndTradeDate(eq(STOCK_CODE), eq(start.minusDays(2))))
            .willReturn(true);

        // when
        domesticDailyPriceService.refreshRecent(STOCK_CODE);

        // then: 이미 있던 하루를 제외한 2건만 저장
        verify(domesticDailyPriceRepository, times(2)).save(any(DomesticDailyPrice.class));
    }

    @Test
    @DisplayName("[재확정 윈도우 밖 - 조회한 기간이 전부 이미 저장돼 있으면 아무것도 저장하지 않는다]")
    void refreshRecent_allAlreadySaved_savesNothing() {
        // given: 윈도우(20일) 밖의 날짜라 존재 여부 체크로 스킵되는 경로를 탄다
        LocalDate outsideWindow = LocalDate.now().minusDays(25);
        TossCandleResponse page = candlePage(1, outsideWindow, null);
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(MIN_LOOKBACK_CANDLES), any())).willReturn(page);
        given(domesticDailyPriceRepository.existsByStockCodeAndTradeDate(anyString(), any())).willReturn(true);

        // when
        domesticDailyPriceService.refreshRecent(STOCK_CODE);

        // then
        verify(domesticDailyPriceRepository, never()).save(any(DomesticDailyPrice.class));
    }

    @Test
    @DisplayName("[이미 목표치만큼 쌓여있으면 API를 호출하지 않는다]")
    void backfillHistoryIfNeeded_alreadySufficient_skipsApiCall() {
        // given
        given(domesticDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(200L);

        // when
        domesticDailyPriceService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then
        verify(tossApiClient, never()).getDailyCandles(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("[부족하면 1페이지(count=200) 조회로 채운다]")
    void backfillHistoryIfNeeded_insufficientSinglePage_fetchesOnce() {
        // given: 기존 0건, 1페이지에서 정확히 목표치(200개)를 반환(전부 윈도우 밖 과거)
        given(domesticDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse page = candlePage(200, LocalDate.now().minusDays(100), null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(page);

        // when
        domesticDailyPriceService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then
        verify(tossApiClient, times(1)).getDailyCandles(eq(STOCK_CODE), eq(200), any());
        verify(domesticDailyPriceRepository, times(200)).save(any(DomesticDailyPrice.class));
    }

    @Test
    @DisplayName("[한 페이지로 부족하면 nextBefore로 다음 페이지를 조회한다]")
    void backfillHistoryIfNeeded_multiplePages_paginatesUntilTargetReached() {
        // given: 목표 250일 - 첫 페이지가 가득 차도(200개) 아직 부족해 두번째 페이지(50개)까지 조회
        given(domesticDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse firstPage = candlePage(200, LocalDate.now().minusDays(100), "cursor-1");
        TossCandleResponse secondPage = candlePage(50, LocalDate.now().minusDays(300), null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(firstPage);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, "cursor-1")).willReturn(secondPage);

        // when
        domesticDailyPriceService.backfillHistoryIfNeeded(STOCK_CODE, 250);

        // then
        verify(tossApiClient, times(2)).getDailyCandles(eq(STOCK_CODE), eq(200), any());
        verify(domesticDailyPriceRepository, times(250)).save(any(DomesticDailyPrice.class));
    }

    @Test
    @DisplayName("[반환 개수가 페이지 크기보다 적으면 더 이상 이력이 없다고 보고 중단한다]")
    void backfillHistoryIfNeeded_shortPage_stopsEvenIfTargetNotReached() {
        // given: 상장한 지 얼마 안 된 종목처럼 30개만 반환(200개 미만)
        given(domesticDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse shortPage = candlePage(30, LocalDate.now().minusDays(100), "cursor-1");
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(shortPage);

        // when
        domesticDailyPriceService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then: 짧은 페이지를 받으면 더 조회하지 않고 종료
        verify(tossApiClient, times(1)).getDailyCandles(anyString(), anyInt(), any());
        verify(domesticDailyPriceRepository, times(30)).save(any(DomesticDailyPrice.class));
    }

    @Test
    @DisplayName("[재확정 윈도우 밖 - 이미 저장된 날짜는 다시 저장하지 않는다]")
    void backfillHistoryIfNeeded_existingDate_skipsSave() {
        // given
        given(domesticDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse page = candlePage(3, LocalDate.now().minusDays(100), null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(page);
        given(domesticDailyPriceRepository.existsByStockCodeAndTradeDate(eq(STOCK_CODE), any()))
            .willReturn(true);

        // when
        domesticDailyPriceService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then
        verify(domesticDailyPriceRepository, never()).save(any(DomesticDailyPrice.class));
    }

    @Test
    @DisplayName("[당일 행이 이미 있으면 스킵하지 않고 최신 확정값으로 덮어쓴다]")
    void refreshRecent_todayAlreadyExists_updatesInPlaceInsteadOfSkipping() {
        // given: 관심종목 등록 시 백필이 장중에 미완성 당일 캔들을 먼저 저장해둔 상태를 가정
        LocalDate today = LocalDate.now();
        DomesticDailyPrice existingIntradaySnapshot = DomesticDailyPrice.of(
            STOCK_CODE, today, 70000L, 70500L, 69500L, 70200L, 500000L);
        TossCandleResponse page = candlePage(1, today, null);
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(MIN_LOOKBACK_CANDLES), any())).willReturn(page);
        given(domesticDailyPriceRepository.findByStockCodeAndTradeDate(STOCK_CODE, today))
            .willReturn(Optional.of(existingIntradaySnapshot));

        // when: 장 마감 배치가 확정 종가로 재수집
        domesticDailyPriceService.refreshRecent(STOCK_CODE);

        // then: exists 체크로 스킵하는 게 아니라 기존 행을 확정 종가로 갱신 후 저장
        verify(domesticDailyPriceRepository, never()).existsByStockCodeAndTradeDate(anyString(), any());
        verify(domesticDailyPriceRepository, times(1)).save(existingIntradaySnapshot);
        assertThat(existingIntradaySnapshot.getClosePrice()).isEqualTo(70500L);
    }

    @Test
    @DisplayName("[재확정 윈도우 안의 과거 거래일 행은 이미 있어도 최신 값으로 덮어쓴다]")
    void refreshRecent_pastDateWithinWindow_overwritesExisting() {
        // given: 2026-07-30 삼성전자 실측 사례를 그대로 재현 - 프리마켓 저거래량
        // 스냅샷(장중 08:23 저장, 거래량 3,856,752)이 재확정 배치(20:10)에서
        // 확정 거래량(30~80M대)으로 갱신되는지 검증
        LocalDate withinWindow = LocalDate.now().minusDays(3);
        DomesticDailyPrice intradaySnapshot = DomesticDailyPrice.of(
            STOCK_CODE, withinWindow, 204000L, 210000L, 202500L, 206500L, 3_856_752L);
        TossCandleResponse page = candlePage(1, withinWindow, null,
            "204000", "211000", "201500", "209500", "31000000");
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(MIN_LOOKBACK_CANDLES), any())).willReturn(page);
        given(domesticDailyPriceRepository.findByStockCodeAndTradeDate(STOCK_CODE, withinWindow))
            .willReturn(Optional.of(intradaySnapshot));

        // when
        domesticDailyPriceService.refreshRecent(STOCK_CODE);

        // then
        verify(domesticDailyPriceRepository, never()).existsByStockCodeAndTradeDate(anyString(), any());
        verify(domesticDailyPriceRepository, times(1)).save(intradaySnapshot);
        assertThat(intradaySnapshot.getVolume()).isEqualTo(31_000_000L);
        assertThat(intradaySnapshot.getClosePrice()).isEqualTo(209500L);
    }

    @Test
    @DisplayName("[재확정 윈도우 안이라도 값이 동일하면 save를 호출하지 않는다]")
    void refreshRecent_pastDateWithinWindow_unchangedValue_skipsSave() {
        // given: 이미 확정된 값과 재조회 값이 완전히 동일한 경우 - 매 스윕마다
        // 종목당 MIN_LOOKBACK_CANDLES개씩 불필요한 UPDATE가 반복되는 걸 막는지 검증
        LocalDate withinWindow = LocalDate.now().minusDays(3);
        DomesticDailyPrice unchanged = DomesticDailyPrice.of(
            STOCK_CODE, withinWindow, 70000L, 71000L, 69000L, 70500L, 1_000_000L);
        TossCandleResponse page = candlePage(1, withinWindow, null,
            "70000", "71000", "69000", "70500", "1000000");
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(MIN_LOOKBACK_CANDLES), any())).willReturn(page);
        given(domesticDailyPriceRepository.findByStockCodeAndTradeDate(STOCK_CODE, withinWindow))
            .willReturn(Optional.of(unchanged));

        // when
        domesticDailyPriceService.refreshRecent(STOCK_CODE);

        // then
        verify(domesticDailyPriceRepository, never()).save(any(DomesticDailyPrice.class));
    }

    @Test
    @DisplayName("[과거 거래일 종가가 가격제한폭을 넘게 바뀌면 전 구간 재백필을 1회 트리거한다]")
    void refreshRecent_closeJumpsBeyondThreshold_triggersRebackfillOnce() {
        // given: 2026-08-03 실측 사례(액면분할 미반영) 재현 - 224원 저장돼 있는데
        // 재조회 시 2240원(정확히 10배)으로 벌어짐
        LocalDate withinWindow = LocalDate.now().minusDays(3);
        DomesticDailyPrice staleSplitPrice = DomesticDailyPrice.of(
            STOCK_CODE, withinWindow, 220L, 230L, 218L, 224L, 0L);
        TossCandleResponse page = candlePage(1, withinWindow, null,
            "2200", "2300", "2180", "2240", "500000");
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(MIN_LOOKBACK_CANDLES), any())).willReturn(page);
        given(domesticDailyPriceRepository.findByStockCodeAndTradeDate(STOCK_CODE, withinWindow))
            .willReturn(Optional.of(staleSplitPrice));
        // 재백필(overwriteAll=true) 경로 - 빈 페이지를 반환해 곧바로 종료시킨다
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(200), eq(null)))
            .willReturn(emptyPage());

        // when
        domesticDailyPriceService.refreshRecent(STOCK_CODE);

        // then: 재조정 감지로 전 구간 재백필이 정확히 1회 트리거된다
        verify(tossApiClient, times(1)).getDailyCandles(eq(STOCK_CODE), eq(200), eq(null));
    }

    @Test
    @DisplayName("[임계값 미만의 변동은 재백필을 트리거하지 않는다]")
    void refreshRecent_closeChangeBelowThreshold_doesNotTriggerRebackfill() {
        // given: 정상적인 하루 변동폭(0.7%) - 가격제한폭(30%)에 한참 못 미침
        LocalDate withinWindow = LocalDate.now().minusDays(3);
        DomesticDailyPrice existing = DomesticDailyPrice.of(
            STOCK_CODE, withinWindow, 70000L, 70800L, 69500L, 70200L, 1_000_000L);
        TossCandleResponse page = candlePage(1, withinWindow, null,
            "70000", "71000", "69500", "70500", "1000000");
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(MIN_LOOKBACK_CANDLES), any())).willReturn(page);
        given(domesticDailyPriceRepository.findByStockCodeAndTradeDate(STOCK_CODE, withinWindow))
            .willReturn(Optional.of(existing));

        // when
        domesticDailyPriceService.refreshRecent(STOCK_CODE);

        // then: 재백필(count=200) 호출이 전혀 없어야 한다
        verify(tossApiClient, never()).getDailyCandles(eq(STOCK_CODE), eq(200), any());
    }

    @Test
    @DisplayName("[재백필(overwriteAll) 경로에서는 재조정을 다시 감지하지 않는다]")
    void rebackfillAdjustedHistory_doesNotRecurse() {
        // given: 재백필 페이지 안에서도 10배 차이가 나는 행이 있지만(감지가 켜져
        // 있었다면 또 재백필을 유발했을 상황), overwriteAll 경로에서는 감지
        // 자체가 꺼져 있어야 한다. 짧은 페이지(2건 < 200)라 자연스럽게 1회로 끝난다
        LocalDate d1 = LocalDate.now().minusDays(1);
        LocalDate d2 = LocalDate.now().minusDays(2);
        TossCandleResponse page = new TossCandleResponse(new TossCandleResponse.TossCandlePageResult(
            List.of(
                candle(d1, "2200", "2300", "2180", "2240", "500000"),
                candle(d2, "2000", "2100", "1980", "2040", "500000")
            ), null));
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(200), eq(null))).willReturn(page);
        given(domesticDailyPriceRepository.findByStockCodeAndTradeDate(eq(STOCK_CODE), eq(d1)))
            .willReturn(Optional.of(DomesticDailyPrice.of(STOCK_CODE, d1, 220L, 230L, 218L, 224L, 0L)));
        given(domesticDailyPriceRepository.findByStockCodeAndTradeDate(eq(STOCK_CODE), eq(d2)))
            .willReturn(Optional.empty());

        // when
        domesticDailyPriceService.rebackfillAdjustedHistory(STOCK_CODE);

        // then: getDailyCandles가 정확히 1번만 호출됐다 - 재귀적으로 또 재백필을
        // 트리거했다면 count=200 호출이 추가로 발생했을 것이다
        verify(tossApiClient, times(1)).getDailyCandles(anyString(), anyInt(), any());
    }

    // Rate Limit(429) 재시도는 2026-08-01부터 TossApiClient.getDailyCandles
    // 안으로 옮겨졌다(TossApiClientTest.getDailyCandles_rateLimited_retriesOnce
    // 참고) - 이 서비스는 이제 그 결과를 그대로 받기만 하므로 여기서
    // 재시도를 별도 검증하지 않는다.

    private TossCandleResponse candlePage(int count, LocalDate startDate, String nextBefore) {
        List<TossCandleResponse.TossCandle> candles = IntStream.range(0, count)
            .mapToObj(i -> candle(startDate.minusDays(i), "70000", "71000", "69000", "70500", "1000000"))
            .collect(Collectors.toList());
        return new TossCandleResponse(new TossCandleResponse.TossCandlePageResult(candles, nextBefore));
    }

    private TossCandleResponse candlePage(int count, LocalDate date, String nextBefore,
                                          String open, String high, String low, String close, String volume) {
        List<TossCandleResponse.TossCandle> candles = IntStream.range(0, count)
            .mapToObj(i -> candle(date, open, high, low, close, volume))
            .collect(Collectors.toList());
        return new TossCandleResponse(new TossCandleResponse.TossCandlePageResult(candles, nextBefore));
    }

    private TossCandleResponse emptyPage() {
        return new TossCandleResponse(new TossCandleResponse.TossCandlePageResult(List.of(), null));
    }

    private TossCandleResponse.TossCandle candle(LocalDate date, String open, String high,
                                                  String low, String close, String volume) {
        return new TossCandleResponse.TossCandle(
            date.atStartOfDay().atOffset(ZoneOffset.ofHours(9)).toString(),
            open, high, low, close, volume, "KRW");
    }
}
