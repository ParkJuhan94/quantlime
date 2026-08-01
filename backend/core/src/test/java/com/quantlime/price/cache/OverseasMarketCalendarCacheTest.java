package com.quantlime.price.cache;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossMarketCalendarResponse.MarketSession;
import com.quantlime.infra.toss.dto.TossUsMarketCalendarResponse;
import com.quantlime.infra.toss.dto.TossUsMarketCalendarResponse.UsMarketCalendarResult;
import com.quantlime.infra.toss.dto.TossUsMarketCalendarResponse.UsMarketDay;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OverseasMarketCalendarCacheTest {

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private OverseasMarketCalendarCache overseasMarketCalendarCache;

    @Test
    @DisplayName("[휴장일(3영업일 전부 regularMarket 없음)이면 개장 중이 아니다]")
    void isMarketOpenNow_allDaysHaveNoRegularMarket_returnsFalse() {
        // given
        UsMarketDay holiday = new UsMarketDay("2026-07-04", null, null, null, null);
        given(tossApiClient.getUsMarketCalendar()).willReturn(
            new TossUsMarketCalendarResponse(new UsMarketCalendarResult(holiday, holiday, holiday)));

        // when
        boolean result = overseasMarketCalendarCache.isMarketOpenNow();

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("[캘린더 응답이 null이면 예외 없이 개장 중이 아닌 것으로 처리한다]")
    void isMarketOpenNow_nullResponse_returnsFalseWithoutThrowing() {
        // given
        given(tossApiClient.getUsMarketCalendar()).willReturn(null);

        // when
        boolean result = overseasMarketCalendarCache.isMarketOpenNow();

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("[오늘의 정규장 시간대 안이면 개장 중이다]")
    void isMarketOpenNow_todayWithinRegularMarket_returnsTrue() {
        // given: 오늘 정규장을 현재 시각 -1시간 ~ +1시간으로 설정, 나머지 이틀은 세션 없음
        OffsetDateTime now = OffsetDateTime.now();
        UsMarketDay today = dayWithRegularMarket(now.minusHours(1), now.plusHours(1));
        UsMarketDay noSession = new UsMarketDay("x", null, null, null, null);
        given(tossApiClient.getUsMarketCalendar()).willReturn(
            new TossUsMarketCalendarResponse(new UsMarketCalendarResult(noSession, today, noSession)));

        // when
        boolean result = overseasMarketCalendarCache.isMarketOpenNow();

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("[미국 정규장이 자정을 넘겨 전영업일 세션에 걸쳐 있으면 개장 중이다]")
    void isMarketOpenNow_withinPreviousBusinessDayRegularMarketCrossingMidnight_returnsTrue() {
        // given: 실제 KST 22:30~다음날 05:00 패턴을 재현 - "어제" 정규장이
        // 지금(현재 시각)까지 이어지고 있는 상황(예: 새벽 3시). previousBusinessDay만
        // 세션을 주고 today/nextBusinessDay는 세션 없음으로 둔다.
        OffsetDateTime now = OffsetDateTime.now();
        UsMarketDay previousBusinessDay = dayWithRegularMarket(now.minusHours(3), now.plusHours(2));
        UsMarketDay noSession = new UsMarketDay("x", null, null, null, null);
        given(tossApiClient.getUsMarketCalendar()).willReturn(
            new TossUsMarketCalendarResponse(
                new UsMarketCalendarResult(previousBusinessDay, noSession, noSession)));

        // when
        boolean result = overseasMarketCalendarCache.isMarketOpenNow();

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("[정규장 시간대 밖이면 개장 중이 아니다]")
    void isMarketOpenNow_outsideAllRegularMarketSessions_returnsFalse() {
        // given: 3영업일 전부 현재 시각과 무관한(이미 지난) 정규장 시간대
        OffsetDateTime now = OffsetDateTime.now();
        UsMarketDay pastDay = dayWithRegularMarket(now.minusHours(10), now.minusHours(8));
        given(tossApiClient.getUsMarketCalendar()).willReturn(
            new TossUsMarketCalendarResponse(new UsMarketCalendarResult(pastDay, pastDay, pastDay)));

        // when
        boolean result = overseasMarketCalendarCache.isMarketOpenNow();

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("[같은 날 여러 번 호출해도 캘린더 조회는 한 번만 한다]")
    void isMarketOpenNow_calledTwiceSameDay_fetchesCalendarOnlyOnce() {
        // given
        OffsetDateTime now = OffsetDateTime.now();
        UsMarketDay today = dayWithRegularMarket(now.minusHours(1), now.plusHours(1));
        given(tossApiClient.getUsMarketCalendar()).willReturn(
            new TossUsMarketCalendarResponse(new UsMarketCalendarResult(today, today, today)));

        // when
        overseasMarketCalendarCache.isMarketOpenNow();
        overseasMarketCalendarCache.isMarketOpenNow();

        // then
        verify(tossApiClient, times(1)).getUsMarketCalendar();
    }

    @Test
    @DisplayName("[캘린더 조회가 예외로 실패하면 예외를 전파하지 않고, 백오프 동안 재호출하지 않는다]")
    void isMarketOpenNow_calendarCallThrows_doesNotRetryEveryTickDuringBackoff() {
        // given
        given(tossApiClient.getUsMarketCalendar())
            .willThrow(new RuntimeException("429 Too Many Requests"));

        // when
        boolean firstCall = overseasMarketCalendarCache.isMarketOpenNow();
        boolean secondCall = overseasMarketCalendarCache.isMarketOpenNow();

        // then
        assertThat(firstCall).isFalse();
        assertThat(secondCall).isFalse();
        verify(tossApiClient, times(1)).getUsMarketCalendar();
    }

    private UsMarketDay dayWithRegularMarket(OffsetDateTime start, OffsetDateTime end) {
        MarketSession regularMarket = new MarketSession(start.toString(), end.toString());
        return new UsMarketDay("2026-07-29", null, null, regularMarket, null);
    }
}
