package com.quantlab.price.cache;

import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossMarketCalendarResponse;
import com.quantlab.infra.toss.dto.TossMarketCalendarResponse.KrMarketCalendarResult;
import com.quantlab.infra.toss.dto.TossMarketCalendarResponse.MarketDay;
import com.quantlab.infra.toss.dto.TossMarketCalendarResponse.MarketSession;
import com.quantlab.infra.toss.dto.TossMarketCalendarResponse.MarketSessions;
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
class MarketCalendarCacheTest {

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private MarketCalendarCache marketCalendarCache;

    @Test
    @DisplayName("[휴장일이면 개장 중이 아니다]")
    void isMarketOpenNow_holiday_returnsFalse() {
        // given: today.integrated가 null인 휴장일 응답
        given(tossApiClient.getMarketCalendar()).willReturn(
            new TossMarketCalendarResponse(
                new KrMarketCalendarResult(new MarketDay("2026-05-05", null))));

        // when
        boolean result = marketCalendarCache.isMarketOpenNow();

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("[영업일이고 현재 시각이 정규장 시간대 안이면 개장 중이다]")
    void isMarketOpenNow_businessDayWithinHours_returnsTrue() {
        // given: 정규장 시간을 현재 시각 -1시간 ~ +1시간으로 설정
        given(tossApiClient.getMarketCalendar()).willReturn(businessDayResponse(-1, 1));

        // when
        boolean result = marketCalendarCache.isMarketOpenNow();

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("[영업일이어도 현재 시각이 정규장 시간대 밖이면 개장 중이 아니다]")
    void isMarketOpenNow_businessDayOutsideHours_returnsFalse() {
        // given: 정규장 시간을 현재 시각 이전(-2시간 ~ -1시간)으로 설정
        given(tossApiClient.getMarketCalendar()).willReturn(businessDayResponse(-2, -1));

        // when
        boolean result = marketCalendarCache.isMarketOpenNow();

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("[같은 날 여러 번 호출해도 캘린더 조회는 한 번만 한다]")
    void isMarketOpenNow_calledTwiceSameDay_fetchesCalendarOnlyOnce() {
        // given
        given(tossApiClient.getMarketCalendar()).willReturn(businessDayResponse(-1, 1));

        // when
        marketCalendarCache.isMarketOpenNow();
        marketCalendarCache.isMarketOpenNow();

        // then
        verify(tossApiClient, times(1)).getMarketCalendar();
    }

    @Test
    @DisplayName("[정규장 시간 밖이어도 NXT 프리마켓 시간대 안이면 개장 중이다]")
    void isMarketOpenNow_withinPreMarketOnly_returnsTrue() {
        // given: 정규장은 현재 시각 이후(+2~+3시간)로 아직 시작 전, 프리마켓만 현재 시각을 포함
        OffsetDateTime now = OffsetDateTime.now();
        MarketSession preMarket = session(now.minusHours(1), now.plusHours(1));
        MarketSession regularMarket = session(now.plusHours(2), now.plusHours(3));
        MarketDay today = new MarketDay(
            now.toLocalDate().toString(),
            new MarketSessions(preMarket, regularMarket, null));
        given(tossApiClient.getMarketCalendar()).willReturn(
            new TossMarketCalendarResponse(new KrMarketCalendarResult(today)));

        // when
        boolean result = marketCalendarCache.isMarketOpenNow();

        // then
        assertThat(result).isTrue();
    }

    private TossMarketCalendarResponse businessDayResponse(long startOffsetHours, long endOffsetHours) {
        OffsetDateTime start = OffsetDateTime.now().plusHours(startOffsetHours);
        OffsetDateTime end = OffsetDateTime.now().plusHours(endOffsetHours);
        MarketSession regularMarket = new MarketSession(start.toString(), end.toString());
        MarketDay today = new MarketDay(
            OffsetDateTime.now().toLocalDate().toString(),
            new MarketSessions(null, regularMarket, null));
        return new TossMarketCalendarResponse(new KrMarketCalendarResult(today));
    }

    private MarketSession session(OffsetDateTime start, OffsetDateTime end) {
        return new MarketSession(start.toString(), end.toString());
    }
}
