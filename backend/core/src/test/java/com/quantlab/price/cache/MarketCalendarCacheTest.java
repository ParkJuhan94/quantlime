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

    private TossMarketCalendarResponse businessDayResponse(long startOffsetHours, long endOffsetHours) {
        OffsetDateTime start = OffsetDateTime.now().plusHours(startOffsetHours);
        OffsetDateTime end = OffsetDateTime.now().plusHours(endOffsetHours);
        MarketSession regularMarket = new MarketSession(start.toString(), end.toString());
        MarketDay today = new MarketDay(
            OffsetDateTime.now().toLocalDate().toString(),
            new MarketSessions(regularMarket));
        return new TossMarketCalendarResponse(new KrMarketCalendarResult(today));
    }
}
