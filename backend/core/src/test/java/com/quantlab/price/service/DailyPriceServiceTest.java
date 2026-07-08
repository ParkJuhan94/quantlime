package com.quantlab.price.service;

import com.quantlab.common.exception.ExternalApiException;
import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossCandleResponse;
import com.quantlab.infra.toss.exception.TossApiErrorCode;
import com.quantlab.price.domain.DailyPrice;
import com.quantlab.price.repository.DailyPriceRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class DailyPriceServiceTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private DailyPriceService dailyPriceService;

    @Test
    @DisplayName("[최근 며칠이 밀려있어도 조회한 기간 내 빠진 날짜를 전부 채운다]")
    void collectDailyPrice_missingRecentDays_savesOnlyNewOnes() {
        // given: 최근 3일치를 조회했는데 그중 1일치는 이미 저장돼 있음
        TossCandleResponse page = candlePage(3, "2026-07-09", null);
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(10), any())).willReturn(page);
        given(dailyPriceRepository.existsByStockCodeAndTradeDate(eq(STOCK_CODE), eq(LocalDate.parse("2026-07-09"))))
            .willReturn(false);
        given(dailyPriceRepository.existsByStockCodeAndTradeDate(eq(STOCK_CODE), eq(LocalDate.parse("2026-07-08"))))
            .willReturn(false);
        given(dailyPriceRepository.existsByStockCodeAndTradeDate(eq(STOCK_CODE), eq(LocalDate.parse("2026-07-07"))))
            .willReturn(true);

        // when
        dailyPriceService.collectDailyPrice(STOCK_CODE);

        // then: 이미 있던 하루를 제외한 2건만 저장
        verify(dailyPriceRepository, times(2)).save(any(DailyPrice.class));
    }

    @Test
    @DisplayName("[조회한 기간이 전부 이미 저장돼 있으면 아무것도 저장하지 않는다]")
    void collectDailyPrice_allAlreadySaved_savesNothing() {
        // given
        TossCandleResponse page = candlePage(1, "2026-07-09", null);
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(10), any())).willReturn(page);
        given(dailyPriceRepository.existsByStockCodeAndTradeDate(anyString(), any())).willReturn(true);

        // when
        dailyPriceService.collectDailyPrice(STOCK_CODE);

        // then
        verify(dailyPriceRepository, never()).save(any(DailyPrice.class));
    }

    @Test
    @DisplayName("[이미 목표치만큼 쌓여있으면 API를 호출하지 않는다]")
    void backfillHistoryIfNeeded_alreadySufficient_skipsApiCall() {
        // given
        given(dailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(200L);

        // when
        dailyPriceService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then
        verify(tossApiClient, never()).getDailyCandles(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("[부족하면 1페이지(count=200) 조회로 채운다]")
    void backfillHistoryIfNeeded_insufficientSinglePage_fetchesOnce() {
        // given: 기존 0건, 1페이지에서 정확히 목표치(200개)를 반환
        given(dailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse page = candlePage(200, "2026-06-01", null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(page);

        // when
        dailyPriceService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then
        verify(tossApiClient, times(1)).getDailyCandles(eq(STOCK_CODE), eq(200), any());
        verify(dailyPriceRepository, times(200)).save(any(DailyPrice.class));
    }

    @Test
    @DisplayName("[한 페이지로 부족하면 nextBefore로 다음 페이지를 조회한다]")
    void backfillHistoryIfNeeded_multiplePages_paginatesUntilTargetReached() {
        // given: 목표 250일 - 첫 페이지가 가득 차도(200개) 아직 부족해 두번째 페이지(50개)까지 조회
        given(dailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse firstPage = candlePage(200, "2026-06-01", "cursor-1");
        TossCandleResponse secondPage = candlePage(50, "2025-11-01", null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(firstPage);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, "cursor-1")).willReturn(secondPage);

        // when
        dailyPriceService.backfillHistoryIfNeeded(STOCK_CODE, 250);

        // then
        verify(tossApiClient, times(2)).getDailyCandles(eq(STOCK_CODE), eq(200), any());
        verify(dailyPriceRepository, times(250)).save(any(DailyPrice.class));
    }

    @Test
    @DisplayName("[반환 개수가 페이지 크기보다 적으면 더 이상 이력이 없다고 보고 중단한다]")
    void backfillHistoryIfNeeded_shortPage_stopsEvenIfTargetNotReached() {
        // given: 상장한 지 얼마 안 된 종목처럼 30개만 반환(200개 미만)
        given(dailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse shortPage = candlePage(30, "2026-06-01", "cursor-1");
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(shortPage);

        // when
        dailyPriceService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then: 짧은 페이지를 받으면 더 조회하지 않고 종료
        verify(tossApiClient, times(1)).getDailyCandles(anyString(), anyInt(), any());
        verify(dailyPriceRepository, times(30)).save(any(DailyPrice.class));
    }

    @Test
    @DisplayName("[이미 저장된 날짜는 다시 저장하지 않는다]")
    void backfillHistoryIfNeeded_existingDate_skipsSave() {
        // given
        given(dailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse page = candlePage(3, "2026-01-01", null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(page);
        given(dailyPriceRepository.existsByStockCodeAndTradeDate(eq(STOCK_CODE), any()))
            .willReturn(true);

        // when
        dailyPriceService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then
        verify(dailyPriceRepository, never()).save(any(DailyPrice.class));
    }

    @Test
    @DisplayName("[Rate Limit(429) 발생 시 대기 후 1회 재시도한다]")
    void backfillHistoryIfNeeded_rateLimited_retriesOnce() {
        // given
        given(dailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse page = candlePage(200, "2026-06-01", null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null))
            .willThrow(new ExternalApiException(TossApiErrorCode.RATE_LIMIT_EXCEEDED))
            .willReturn(page);

        // when
        dailyPriceService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then
        verify(tossApiClient, times(2)).getDailyCandles(eq(STOCK_CODE), eq(200), any());
        verify(dailyPriceRepository, times(200)).save(any(DailyPrice.class));
    }

    private TossCandleResponse candlePage(int count, String startDate, String nextBefore) {
        LocalDate start = LocalDate.parse(startDate);
        List<TossCandleResponse.TossCandle> candles = IntStream.range(0, count)
            .mapToObj(i -> new TossCandleResponse.TossCandle(
                start.minusDays(i).atStartOfDay().atOffset(ZoneOffset.ofHours(9)).toString(),
                "70000", "71000", "69000", "70500", "1000000", "KRW"
            ))
            .collect(Collectors.toList());
        return new TossCandleResponse(new TossCandleResponse.TossCandlePageResult(candles, nextBefore));
    }
}
