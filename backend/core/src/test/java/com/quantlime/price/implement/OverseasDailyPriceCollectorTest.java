package com.quantlime.price.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.price.domain.OverseasDailyPrice;
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
import org.springframework.dao.DataIntegrityViolationException;

/**
 * KIS -> Toss 캔들로 이관(2026-07-29) 이후의 회귀 테스트 - 구조는
 * {@link DomesticDailyPriceServiceTest}와 동일한 count/before 커서 방식으로 통일됐다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OverseasDailyPriceCollectorTest {

    private static final String STOCK_CODE = "AAPL";

    @Mock
    private DailyPriceReader dailyPriceReader;

    @Mock
    private DailyPriceAppender dailyPriceAppender;

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private OverseasDailyPriceCollector overseasDailyPriceCollector;

    @Test
    @DisplayName("[이미 목표치만큼 쌓여있으면 API를 호출하지 않는다]")
    void backfillHistoryIfNeeded_alreadySufficient_skipsApiCall() {
        // given
        given(dailyPriceReader.countOverseas(STOCK_CODE)).willReturn(200L);

        // when
        overseasDailyPriceCollector.backfillHistory(STOCK_CODE, 200);

        // then
        verify(tossApiClient, never()).getDailyCandles(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("[부족하면 1페이지(count=200) 조회로 채운다]")
    void backfillHistoryIfNeeded_insufficientSinglePage_fetchesOnce() {
        // given
        given(dailyPriceReader.countOverseas(STOCK_CODE)).willReturn(0L);
        TossCandleResponse page = candlePage(200, "2026-06-01", null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(page);

        // when
        overseasDailyPriceCollector.backfillHistory(STOCK_CODE, 200);

        // then
        verify(tossApiClient, times(1)).getDailyCandles(eq(STOCK_CODE), eq(200), any());
        verify(dailyPriceAppender, times(200)).saveOverseas(any(OverseasDailyPrice.class));
    }

    @Test
    @DisplayName("[한 페이지로 부족하면 nextBefore로 다음 페이지를 조회한다]")
    void backfillHistoryIfNeeded_multiplePages_paginatesUntilTargetReached() {
        // given
        given(dailyPriceReader.countOverseas(STOCK_CODE)).willReturn(0L);
        TossCandleResponse firstPage = candlePage(200, "2026-06-01", "cursor-1");
        TossCandleResponse secondPage = candlePage(50, "2025-11-01", null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(firstPage);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, "cursor-1")).willReturn(secondPage);

        // when
        overseasDailyPriceCollector.backfillHistory(STOCK_CODE, 250);

        // then
        verify(tossApiClient, times(2)).getDailyCandles(eq(STOCK_CODE), eq(200), any());
        verify(dailyPriceAppender, times(250)).saveOverseas(any(OverseasDailyPrice.class));
    }

    @Test
    @DisplayName("[반환 개수가 페이지 크기보다 적으면 더 이상 이력이 없다고 보고 중단한다]")
    void backfillHistoryIfNeeded_shortPage_stopsEvenIfTargetNotReached() {
        // given
        given(dailyPriceReader.countOverseas(STOCK_CODE)).willReturn(0L);
        TossCandleResponse shortPage = candlePage(30, "2026-06-01", "cursor-1");
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(shortPage);

        // when
        overseasDailyPriceCollector.backfillHistory(STOCK_CODE, 200);

        // then
        verify(tossApiClient, times(1)).getDailyCandles(anyString(), anyInt(), any());
        verify(dailyPriceAppender, times(30)).saveOverseas(any(OverseasDailyPrice.class));
    }

    @Test
    @DisplayName("[이미 저장된 날짜는 다시 저장하지 않는다]")
    void backfillHistoryIfNeeded_existingDate_skipsSave() {
        // given
        given(dailyPriceReader.countOverseas(STOCK_CODE)).willReturn(0L);
        TossCandleResponse page = candlePage(3, "2026-01-01", null);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null)).willReturn(page);
        given(dailyPriceReader.existsOverseas(eq(STOCK_CODE), any()))
            .willReturn(true);

        // when
        overseasDailyPriceCollector.backfillHistory(STOCK_CODE, 200);

        // then
        verify(dailyPriceAppender, never()).saveOverseas(any(OverseasDailyPrice.class));
    }

    @Test
    @DisplayName("[refreshRecent는 최소 재확정 캔들 수(20) 미만이면 20으로 올려 조회한다]")
    void refreshRecent_fetchesWithGivenLookbackDays() {
        // given: lookbackDays(8)가 DailyPriceSettlementPolicy.MIN_LOOKBACK_CANDLES(20)보다
        // 작아 20으로 상향된다 - 갭이 작아도 재확정 윈도우 전체를 훑어야 미확정
        // 스냅샷/수정주가 재조정을 놓치지 않는다(국내 DomesticDailyPriceService와 동일 정책)
        TossCandleResponse page = candlePage(20, "2026-06-01", null);
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(20), any())).willReturn(page);
        given(dailyPriceReader.existsOverseas(eq(STOCK_CODE), any()))
            .willReturn(false);

        // when
        overseasDailyPriceCollector.refreshRecent(STOCK_CODE, 8);

        // then
        verify(tossApiClient, times(1)).getDailyCandles(eq(STOCK_CODE), eq(20), any());
        verify(dailyPriceAppender, times(20)).saveOverseas(any(OverseasDailyPrice.class));
    }

    @Test
    @DisplayName("[refreshRecent - 응답 캔들이 비어 있으면 아무것도 저장하지 않는다]")
    void refreshRecent_emptyCandles_savesNothing() {
        // given
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(20), any()))
            .willReturn(candlePage(0, "2026-06-01", null));

        // when
        overseasDailyPriceCollector.refreshRecent(STOCK_CODE, 8);

        // then
        verify(dailyPriceAppender, never()).saveOverseas(any(OverseasDailyPrice.class));
    }

    @Test
    @DisplayName("[재확정 윈도우 안 - 기존 행과 값이 다르면 덮어써서 저장하고 재백필은 하지 않는다]")
    void refreshRecent_withinWindowChangedRow_overwritesWithoutRebackfill() {
        // given: 종가 140 -> 151 (+7.9%)은 재조정 임계값(50%) 미만
        LocalDate today = LocalDate.now();
        OverseasDailyPrice existing = existingRow(today, 140.0);
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(20), any()))
            .willReturn(candlePage(1, today.toString(), null));
        given(dailyPriceReader.findOverseas(STOCK_CODE, today)).willReturn(Optional.of(existing));

        // when
        overseasDailyPriceCollector.refreshRecent(STOCK_CODE, 8);

        // then
        assertThat(existing.getClosePrice()).isEqualTo(151.0);
        verify(dailyPriceAppender).saveOverseas(existing);
        verify(tossApiClient, times(1)).getDailyCandles(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("[재확정 윈도우 안 - 기존 행과 값이 같으면 저장하지 않는다]")
    void refreshRecent_withinWindowUnchangedRow_skipsSave() {
        // given
        LocalDate today = LocalDate.now();
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(20), any()))
            .willReturn(candlePage(1, today.toString(), null));
        given(dailyPriceReader.findOverseas(STOCK_CODE, today))
            .willReturn(Optional.of(existingRow(today, 151.0)));

        // when
        overseasDailyPriceCollector.refreshRecent(STOCK_CODE, 8);

        // then
        verify(dailyPriceAppender, never()).saveOverseas(any(OverseasDailyPrice.class));
    }

    @Test
    @DisplayName("[종가가 재조정 임계값(50%) 이상 바뀌면 덮어쓴 뒤 전 구간 재백필을 1회 트리거한다]")
    void refreshRecent_restatement_triggersRebackfillOnce() {
        // given: 100 -> 151 (+51%)
        LocalDate today = LocalDate.now();
        OverseasDailyPrice existing = existingRow(today, 100.0);
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(20), any()))
            .willReturn(candlePage(1, today.toString(), null));
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null))
            .willReturn(candlePage(1, today.toString(), null));
        given(dailyPriceReader.findOverseas(STOCK_CODE, today)).willReturn(Optional.of(existing));

        // when
        overseasDailyPriceCollector.refreshRecent(STOCK_CODE, 8);

        // then: 재백필 경로는 재조정을 다시 감지하지 않으므로 호출은 총 2회로 끝난다
        verify(tossApiClient, times(1)).getDailyCandles(STOCK_CODE, 200, null);
        verify(tossApiClient, times(2)).getDailyCandles(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("[윈도우 안 신규 행 저장이 중복키 충돌이면 예외 없이 건너뛴다]")
    void refreshRecent_insertConflict_isSwallowed() {
        // given
        LocalDate today = LocalDate.now();
        given(tossApiClient.getDailyCandles(eq(STOCK_CODE), eq(20), any()))
            .willReturn(candlePage(1, today.toString(), null));
        given(dailyPriceReader.findOverseas(STOCK_CODE, today)).willReturn(Optional.empty());
        willThrow(new DataIntegrityViolationException("dup"))
            .given(dailyPriceAppender).saveOverseas(any(OverseasDailyPrice.class));

        // when & then
        assertThatCode(() -> overseasDailyPriceCollector.refreshRecent(STOCK_CODE, 8))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[윈도우 밖 이력 저장이 중복키 충돌이면 예외 없이 건너뛴다]")
    void backfillHistory_outsideWindowInsertConflict_isSwallowed() {
        // given
        given(dailyPriceReader.countOverseas(STOCK_CODE)).willReturn(0L);
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null))
            .willReturn(candlePage(2, "2020-01-10", null));
        willThrow(new DataIntegrityViolationException("dup"))
            .given(dailyPriceAppender).saveOverseas(any(OverseasDailyPrice.class));

        // when & then
        assertThatCode(() -> overseasDailyPriceCollector.backfillHistory(STOCK_CODE, 200))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[rebackfill - 커서를 따라 여러 페이지를 받아 기존 행을 덮어쓰고 신규 건수를 돌려준다]")
    void rebackfill_paginatesAndReturnsCreatedCount() {
        // given: 1페이지 200건(기존 행 없음 -> 신규), 2페이지 50건
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null))
            .willReturn(candlePage(200, "2026-06-01", "cursor-1"));
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, "cursor-1"))
            .willReturn(candlePage(50, "2025-11-01", null));

        // when
        int created = overseasDailyPriceCollector.rebackfill(STOCK_CODE, 400);

        // then
        assertThat(created).isEqualTo(250);
        verify(dailyPriceAppender, times(250)).saveOverseas(any(OverseasDailyPrice.class));
    }

    @Test
    @DisplayName("[rebackfill - 빈 페이지가 오면 즉시 중단한다]")
    void rebackfill_emptyPage_stops() {
        // given
        given(tossApiClient.getDailyCandles(STOCK_CODE, 200, null))
            .willReturn(candlePage(0, "2026-06-01", null));

        // when
        int created = overseasDailyPriceCollector.rebackfill(STOCK_CODE, 400);

        // then
        assertThat(created).isZero();
        verify(dailyPriceAppender, never()).saveOverseas(any(OverseasDailyPrice.class));
    }

    private OverseasDailyPrice existingRow(LocalDate tradeDate, double close) {
        return OverseasDailyPrice.of(STOCK_CODE, tradeDate, 150.0, 152.0, 148.0, close, 1000000L);
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
