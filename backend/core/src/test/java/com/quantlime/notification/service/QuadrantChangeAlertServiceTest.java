package com.quantlime.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.notification.cache.QuadrantAlertSentStore;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.score.domain.Quadrant;
import com.quantlime.score.domain.Score;
import com.quantlime.score.implement.ScoreReader;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.implement.StockReader;
import com.quantlime.watchlist.implement.WatchlistReader;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class QuadrantChangeAlertServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @Mock
    private WatchlistReader watchlistReader;
    @Mock
    private ScoreReader scoreReader;
    @Mock
    private StockReader stockReader;
    @Mock
    private FcmPushService fcmPushService;
    @Mock
    private QuadrantAlertSentStore sentStore;

    @InjectMocks
    private QuadrantChangeAlertService service;

    private Score score(String code, LocalDate date, Quadrant quadrant) {
        return Score.of(code, date, 50.0, 50.0, 50.0, null, quadrant, null, false);
    }

    private Stock stock(String code, String name) {
        Stock stock = mock(Stock.class);
        lenient().when(stock.getStockCode()).thenReturn(code);
        lenient().when(stock.getDisplayName()).thenReturn(name);
        return stock;
    }

    @Test
    @DisplayName("[알림을 켠 그룹의 종목이 없으면 스코어 조회 없이 0을 반환한다]")
    void noOptedInStocks_returnsZero() {
        given(watchlistReader.findStockCodesInQuadrantAlertGroups(USER_ID)).willReturn(List.of());

        assertThat(service.notifyUser(USER_ID, TODAY)).isZero();

        verifyNoInteractions(scoreReader, fcmPushService);
    }

    @Test
    @DisplayName("[최신 행과 직전 행의 사분면이 다른 종목만 한 건의 요약 알림으로 보낸다]")
    void changedQuadrant_sendsSingleSummary() {
        given(watchlistReader.findStockCodesInQuadrantAlertGroups(USER_ID)).willReturn(List.of("005930", "000660"));
        given(scoreReader.findLatestScoresOnOrBefore(List.of("005930", "000660"), TODAY)).willReturn(List.of(
            score("005930", TODAY, Quadrant.TREND_UP_OVERBOUGHT),
            score("000660", TODAY, Quadrant.TREND_UP_OVERSOLD)));
        given(scoreReader.findLatestScoresOnOrBefore(List.of("005930", "000660"), YESTERDAY)).willReturn(List.of(
            score("005930", YESTERDAY, Quadrant.TREND_UP_OVERSOLD),
            score("000660", YESTERDAY, Quadrant.TREND_UP_OVERSOLD)));
        given(sentStore.markIfNew(USER_ID, "005930", TODAY)).willReturn(true);
        Stock samsung = stock("005930", "삼성전자");
        given(stockReader.findByStockCodeIn(List.of("005930"))).willReturn(List.of(samsung));

        int count = service.notifyUser(USER_ID, TODAY);

        assertThat(count).isEqualTo(1);
        verify(fcmPushService).sendToUser(eq(USER_ID), eq(NotificationType.QUADRANT_CHANGE),
            eq("관심종목 사분면 변화 1건"), eq("삼성전자 상승추세 눌림목→추세 연장·과열"), anyString());
        verify(sentStore, never()).markIfNew(USER_ID, "000660", TODAY);
    }

    @Test
    @DisplayName("[이미 통지한 (종목, 산출일)이면 다시 보내지 않는다 - 재전달·재실행 멱등]")
    void alreadyNotified_isSkipped() {
        given(watchlistReader.findStockCodesInQuadrantAlertGroups(USER_ID)).willReturn(List.of("005930"));
        given(scoreReader.findLatestScoresOnOrBefore(List.of("005930"), TODAY))
            .willReturn(List.of(score("005930", TODAY, Quadrant.TREND_DOWN_OVERSOLD)));
        given(scoreReader.findLatestScoresOnOrBefore(List.of("005930"), YESTERDAY))
            .willReturn(List.of(score("005930", YESTERDAY, Quadrant.TREND_UP_OVERSOLD)));
        given(sentStore.markIfNew(USER_ID, "005930", TODAY)).willReturn(false);

        assertThat(service.notifyUser(USER_ID, TODAY)).isZero();

        verifyNoInteractions(fcmPushService);
    }

    @Test
    @DisplayName("[직전 행이 없거나 사분면이 null이거나 오래된 행이면 판정에서 제외한다]")
    void missingPreviousNullQuadrantOrStale_areExcluded() {
        LocalDate stale = TODAY.minusDays(10);
        given(watchlistReader.findStockCodesInQuadrantAlertGroups(USER_ID))
            .willReturn(List.of("NEW", "NULLQ", "OLD"));
        given(scoreReader.findLatestScoresOnOrBefore(anyList(), eq(TODAY))).willReturn(List.of(
            score("NEW", TODAY, Quadrant.TREND_UP_OVERSOLD),
            score("NULLQ", TODAY, null),
            score("OLD", stale, Quadrant.TREND_UP_OVERSOLD)));
        given(scoreReader.findLatestScoresOnOrBefore(anyList(), eq(YESTERDAY))).willReturn(List.of());

        assertThat(service.notifyUser(USER_ID, TODAY)).isZero();

        verifyNoInteractions(sentStore, fcmPushService);
    }

    @Test
    @DisplayName("[국내(당일)·해외(전 거래일)처럼 산출일이 달라도 종목군별로 직전 행과 비교한다]")
    void differentScoreDates_comparedPerDate() {
        LocalDate overseasDate = YESTERDAY;
        given(watchlistReader.findStockCodesInQuadrantAlertGroups(USER_ID)).willReturn(List.of("005930", "AAPL"));
        given(scoreReader.findLatestScoresOnOrBefore(List.of("005930", "AAPL"), TODAY)).willReturn(List.of(
            score("005930", TODAY, Quadrant.TREND_UP_OVERBOUGHT),
            score("AAPL", overseasDate, Quadrant.TREND_DOWN_OVERBOUGHT)));
        given(scoreReader.findLatestScoresOnOrBefore(List.of("005930"), YESTERDAY))
            .willReturn(List.of(score("005930", YESTERDAY, Quadrant.TREND_UP_OVERSOLD)));
        given(scoreReader.findLatestScoresOnOrBefore(List.of("AAPL"), overseasDate.minusDays(1)))
            .willReturn(List.of(score("AAPL", overseasDate.minusDays(1), Quadrant.TREND_UP_OVERSOLD)));
        given(sentStore.markIfNew(eq(USER_ID), anyString(), any(LocalDate.class))).willReturn(true);
        Stock samsung = stock("005930", "삼성전자");
        Stock apple = stock("AAPL", "애플");
        given(stockReader.findByStockCodeIn(anyList())).willReturn(List.of(samsung, apple));

        assertThat(service.notifyUser(USER_ID, TODAY)).isEqualTo(2);

        verify(fcmPushService).sendToUser(eq(USER_ID), eq(NotificationType.QUADRANT_CHANGE),
            eq("관심종목 사분면 변화 2건"), anyString(), anyString());
    }

    @Test
    @DisplayName("[4건 이상이면 앞의 3건만 이름으로 보여주고 나머지는 '외 N건'으로 줄인다]")
    void manyChanges_truncatesContent() {
        List<String> codes = List.of("A", "B", "C", "D", "E");
        given(watchlistReader.findStockCodesInQuadrantAlertGroups(USER_ID)).willReturn(codes);
        given(scoreReader.findLatestScoresOnOrBefore(codes, TODAY)).willReturn(codes.stream()
            .map(code -> score(code, TODAY, Quadrant.TREND_UP_OVERBOUGHT)).toList());
        given(scoreReader.findLatestScoresOnOrBefore(codes, YESTERDAY)).willReturn(codes.stream()
            .map(code -> score(code, YESTERDAY, Quadrant.TREND_UP_OVERSOLD)).toList());
        given(sentStore.markIfNew(eq(USER_ID), anyString(), eq(TODAY))).willReturn(true);
        given(stockReader.findByStockCodeIn(anyList())).willReturn(List.of());

        service.notifyUser(USER_ID, TODAY);

        verify(fcmPushService).sendToUser(eq(USER_ID), eq(NotificationType.QUADRANT_CHANGE),
            eq("관심종목 사분면 변화 5건"), contains("외 2건"), anyString());
    }

    @Test
    @DisplayName("[발송이 실패하면 장부를 되돌리고 예외를 다시 던져 Kafka 재시도에서 재통지되게 한다]")
    void sendFails_unmarksAndRethrows() {
        given(watchlistReader.findStockCodesInQuadrantAlertGroups(USER_ID)).willReturn(List.of("005930"));
        given(scoreReader.findLatestScoresOnOrBefore(List.of("005930"), TODAY))
            .willReturn(List.of(score("005930", TODAY, Quadrant.TREND_UP_OVERBOUGHT)));
        given(scoreReader.findLatestScoresOnOrBefore(List.of("005930"), YESTERDAY))
            .willReturn(List.of(score("005930", YESTERDAY, Quadrant.TREND_UP_OVERSOLD)));
        given(sentStore.markIfNew(USER_ID, "005930", TODAY)).willReturn(true);
        Stock samsung = stock("005930", "삼성전자");
        given(stockReader.findByStockCodeIn(anyList())).willReturn(List.of(samsung));
        willThrow(new IllegalStateException("fcm down")).given(fcmPushService)
            .sendToUser(any(), any(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.notifyUser(USER_ID, TODAY)).isInstanceOf(IllegalStateException.class);

        verify(sentStore).unmark(USER_ID, "005930", TODAY);
    }
}
