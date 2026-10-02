package com.quantlime.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quantlime.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class RankingPeriodTest {

    @Test
    @DisplayName("[코드 문자열로 기간을 찾고 일수/토스 duration이 매핑된다]")
    void of_validCode_returnsPeriod() {
        assertThat(RankingPeriod.of("1w")).isEqualTo(RankingPeriod.WEEK);
        assertThat(RankingPeriod.of("1w").getDays()).isEqualTo(7);
        assertThat(RankingPeriod.of("realtime").getTossDuration()).isEqualTo("1d");
    }

    @Test
    @DisplayName("[실시간/1일만 intraday다]")
    void isIntraday_onlyRealtimeAndDay() {
        assertThat(RankingPeriod.REALTIME.isIntraday()).isTrue();
        assertThat(RankingPeriod.DAY.isIntraday()).isTrue();
        assertThat(RankingPeriod.WEEK.isIntraday()).isFalse();
    }

    @Test
    @DisplayName("[알 수 없는 코드면 ValidationException을 던진다]")
    void of_unknownCode_throws() {
        assertThatThrownBy(() -> RankingPeriod.of("2w")).isInstanceOf(ValidationException.class);
    }
}
