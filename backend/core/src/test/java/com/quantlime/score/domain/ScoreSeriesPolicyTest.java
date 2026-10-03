package com.quantlime.score.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ScoreSeriesPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    @Test
    @DisplayName("[시계열의 최신 날짜는 윈도우 밖이어도 항상 덮어쓴다]")
    void latestDate_alwaysOverwritten() {
        LocalDate latest = TODAY.minusDays(60);

        assertThat(ScoreSeriesPolicy.shouldOverwrite(latest, latest, TODAY)).isTrue();
    }

    @Test
    @DisplayName("[재확정 윈도우(20일) 안의 과거 날짜는 덮어쓴다]")
    void withinWindow_overwritten() {
        assertThat(ScoreSeriesPolicy.shouldOverwrite(TODAY.minusDays(20), TODAY, TODAY)).isTrue();
    }

    @Test
    @DisplayName("[윈도우보다 오래된 날짜는 덮어쓰지 않는다]")
    void beforeWindow_notOverwritten() {
        assertThat(ScoreSeriesPolicy.shouldOverwrite(TODAY.minusDays(21), TODAY, TODAY)).isFalse();
    }
}
