package com.quantlime.price.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class DailyPriceSettlementPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    @Test
    @DisplayName("[재확정 윈도우는 오늘로부터 20일 전까지(경계일 포함)다]")
    void isWithinWindow_boundary() {
        assertThat(DailyPriceSettlementPolicy.windowStart(TODAY)).isEqualTo(TODAY.minusDays(20));
        assertThat(DailyPriceSettlementPolicy.isWithinWindow(TODAY.minusDays(20), TODAY)).isTrue();
        assertThat(DailyPriceSettlementPolicy.isWithinWindow(TODAY.minusDays(21), TODAY)).isFalse();
        assertThat(DailyPriceSettlementPolicy.isWithinWindow(TODAY, TODAY)).isTrue();
    }

    @Test
    @DisplayName("[확정 판정: 같은 거래일 20:00 이후 갱신분만 확정, 그 이전(NXT 애프터 진행 중)은 미확정]")
    void isSettled_boundaryAtTwentyOClock() {
        LocalDate tradeDate = LocalDate.of(2026, 9, 29);

        assertThat(DailyPriceSettlementPolicy.isSettled(tradeDate, tradeDate.atTime(20, 0))).isTrue();
        assertThat(DailyPriceSettlementPolicy.isSettled(tradeDate, tradeDate.atTime(19, 59, 59))).isFalse();
        assertThat(DailyPriceSettlementPolicy.isSettled(tradeDate, tradeDate.atTime(16, 0))).isFalse();
        assertThat(DailyPriceSettlementPolicy.isSettled(tradeDate, tradeDate.plusDays(1).atTime(0, 5))).isTrue();
    }

    @Test
    @DisplayName("[updatedAt이 null이면 안전한 쪽(미확정, 재조회)으로 판단한다]")
    void nullUpdatedAt_isTreatedAsUnsettledAndNotRecent() {
        assertThat(DailyPriceSettlementPolicy.isSettled(TODAY, null)).isFalse();
        assertThat(DailyPriceSettlementPolicy.isRecentlyRefreshed(null, LocalDateTime.of(2026, 9, 30, 12, 0)))
            .isFalse();
    }

    @Test
    @DisplayName("[최근 재조회 가드는 60분 미만일 때만 true(정확히 60분은 false)]")
    void isRecentlyRefreshed_sixtyMinuteBoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 30, 12, 0);

        assertThat(DailyPriceSettlementPolicy.isRecentlyRefreshed(now.minusMinutes(59), now)).isTrue();
        assertThat(DailyPriceSettlementPolicy.isRecentlyRefreshed(now.minusMinutes(60), now)).isFalse();
    }

    @Test
    @DisplayName("[재조정 판정: 변동률 절대값이 임계값 이상이면 true, 상한가(+30%)는 오탐하지 않는다]")
    void isRestatement_thresholdBoundary() {
        double threshold = DailyPriceSettlementPolicy.DOMESTIC_RESTATEMENT_THRESHOLD;

        assertThat(DailyPriceSettlementPolicy.isRestatement(1000, 1300, threshold)).isFalse(); // 상한가
        assertThat(DailyPriceSettlementPolicy.isRestatement(1000, 700, threshold)).isFalse(); // 하한가
        assertThat(DailyPriceSettlementPolicy.isRestatement(1000, 1350, threshold)).isTrue(); // 경계 포함
        assertThat(DailyPriceSettlementPolicy.isRestatement(1000, 650, threshold)).isTrue();
        assertThat(DailyPriceSettlementPolicy.isRestatement(1000, 200, threshold)).isTrue(); // 5:1 액면분할
        assertThat(DailyPriceSettlementPolicy.isRestatement(1000, 5000, threshold)).isTrue(); // 병합
    }

    @Test
    @DisplayName("[저장된 종가가 0이면 나눗셈 없이 false]")
    void isRestatement_zeroStoredClose_returnsFalse() {
        assertThat(DailyPriceSettlementPolicy.isRestatement(0, 1000, 0.35)).isFalse();
    }
}
