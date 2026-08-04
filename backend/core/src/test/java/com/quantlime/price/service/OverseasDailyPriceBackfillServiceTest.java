package com.quantlime.price.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
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

/**
 * KIS -> Toss 캔들로 이관(2026-07-29) 이후의 회귀 테스트 - 구조는
 * {@link DomesticDailyPriceServiceTest}와 동일한 count/before 커서 방식으로 통일됐다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OverseasDailyPriceBackfillServiceTest {

    private static final String STOCK_CODE = "AAPL";

    @Mock
    private OverseasDailyPriceRepository overseasDailyPriceRepository;

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;

    @Test
    @DisplayName("[이미 목표치만큼 쌓여있으면 API를 호출하지 않는다]")
    void backfillHistoryIfNeeded_alreadySufficient_skipsApiCall() {
        // given
        given(overseasDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(200L);

        // when
        overseasDailyPriceBackfillService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then
        verify(tossApiClient, never()).getDailyCandles(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("[부족하면 1페이지(count=200) 조회로 채운다]")
    void backfillHistoryIfNeeded_insufficientSinglePage_fetchesOnce() {
        // given
        given(overseasDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse page = candlePage(200, "2026-06-01", null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(page);

        // when
        overseasDailyPriceBackfillService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then
        verify(tossApiClient, times(1)).getDailyCandles(eq(STOCK_CODE), eq(200), any());
        verify(overseasDailyPriceRepository, times(200)).save(any(OverseasDailyPrice.class));
    }

    @Test
    @DisplayName("[한 페이지로 부족하면 nextBefore로 다음 페이지를 조회한다]")
    void backfillHistoryIfNeeded_multiplePages_paginatesUntilTargetReached() {
        // given
        given(overseasDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse firstPage = candlePage(200, "2026-06-01", "cursor-1");
        TossCandleResponse secondPage = candlePage(50, "2025-11-01", null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(firstPage);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, "cursor-1")).willReturn(secondPage);

        // when
        overseasDailyPriceBackfillService.backfillHistoryIfNeeded(STOCK_CODE, 250);

        // then
        verify(tossApiClient, times(2)).getDailyCandles(eq(STOCK_CODE), eq(200), any());
        verify(overseasDailyPriceRepository, times(250)).save(any(OverseasDailyPrice.class));
    }

    @Test
    @DisplayName("[반환 개수가 페이지 크기보다 적으면 더 이상 이력이 없다고 보고 중단한다]")
    void backfillHistoryIfNeeded_shortPage_stopsEvenIfTargetNotReached() {
        // given
        given(overseasDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse shortPage = candlePage(30, "2026-06-01", "cursor-1");
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(shortPage);

        // when
        overseasDailyPriceBackfillService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then
        verify(tossApiClient, times(1)).getDailyCandles(anyString(), anyInt(), any());
        verify(overseasDailyPriceRepository, times(30)).save(any(OverseasDailyPrice.class));
    }

    @Test
    @DisplayName("[이미 저장된 날짜는 다시 저장하지 않는다]")
    void backfillHistoryIfNeeded_existingDate_skipsSave() {
        // given
        given(overseasDailyPriceRepository.countByStockCode(STOCK_CODE)).willReturn(0L);
        TossCandleResponse page = candlePage(3, "2026-01-01", null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(page);
        given(overseasDailyPriceRepository.existsByStockCodeAndTradeDate(eq(STOCK_CODE), any()))
            .willReturn(true);

        // when
        overseasDailyPriceBackfillService.backfillHistoryIfNeeded(STOCK_CODE, 200);

        // then
        verify(overseasDailyPriceRepository, never()).save(any(OverseasDailyPrice.class));
    }

    @Test
    @DisplayName("[refreshRecent는 최소 재확정 캔들 수(20) 미만이면 20으로 올려 조회한다]")
    void refreshRecent_fetchesWithGivenLookbackDays() {
        // given: lookbackDays(8)가 DailyPriceSettlementPolicy.MIN_LOOKBACK_CANDLES(20)보다
        // 작아 20으로 상향된다 - 갭이 작아도 재확정 윈도우 전체를 훑어야 미확정
        // 스냅샷/수정주가 재조정을 놓치지 않는다(국내 DomesticDailyPriceService와 동일 정책)
        TossCandleResponse page = candlePage(20, "2026-06-01", null);
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(20), any())).willReturn(page);
        given(overseasDailyPriceRepository.existsByStockCodeAndTradeDate(eq(STOCK_CODE), any()))
            .willReturn(false);

        // when
        overseasDailyPriceBackfillService.refreshRecent(STOCK_CODE, 8);

        // then
        verify(tossApiClient, times(1)).getDailyCandles(eq(STOCK_CODE), eq(20), any());
        verify(overseasDailyPriceRepository, times(20)).save(any(OverseasDailyPrice.class));
    }

    // Rate Limit(429) 재시도는 2026-08-01부터 TossApiClient.getDailyCandles
    // 안으로 옮겨졌다(TossApiClientTest.getDailyCandles_rateLimited_retriesOnce
    // 참고) - 이 서비스는 이제 그 결과를 그대로 받기만 하므로 여기서
    // 재시도를 별도 검증하지 않는다.

    private TossCandleResponse candlePage(int count, String startDate, String nextBefore) {
        LocalDate start = LocalDate.parse(startDate);
        List<TossCandleResponse.TossCandle> candles = IntStream.range(0, count)
            .mapToObj(i -> new TossCandleResponse.TossCandle(
                start.minusDays(i).atStartOfDay().atOffset(ZoneOffset.ofHours(9)).toString(),
                "150.00", "152.00", "148.00", "151.00", "1000000", "USD"
            ))
            .collect(Collectors.toList());
        return new TossCandleResponse(new TossCandleResponse.TossCandlePageResult(candles, nextBefore));
    }
}
