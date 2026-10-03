package com.quantlime.price.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 일봉 upsert 규칙(정규장 종가 보호·값 동일 생략·재조정 감지)을 I/O 없이 엔티티 단위로 고정한다. */
@Tag("unit")
class DomesticDailyPriceReconcileTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 2);

    private DomesticDailyPrice price(long open, long high, long low, long close, long volume) {
        return DomesticDailyPrice.of("005930", DATE, open, high, low, close, volume);
    }

    @Test
    @DisplayName("[값이 그대로면 UNCHANGED]")
    void unchanged() {
        DomesticDailyPrice p = price(100, 110, 90, 105, 1000);

        assertThat(p.reconcile(100L, 110L, 90L, 105L, 1000L, false)).isEqualTo(CandleReconcileResult.UNCHANGED);
    }

    @Test
    @DisplayName("[값이 바뀌면 UPDATED이고 close까지 덮어쓰며 보호 플래그는 해제된다]")
    void updated_overwritesClose() {
        DomesticDailyPrice p = price(100, 110, 90, 105, 1000);

        assertThat(p.reconcile(101L, 112L, 89L, 108L, 1200L, false)).isEqualTo(CandleReconcileResult.UPDATED);
        assertThat(p.getClosePrice()).isEqualTo(108L);
        assertThat(p.isRegularCloseConfirmed()).isFalse();
    }

    @Test
    @DisplayName("[종가 변동이 재조정 임계값(35%) 이상이면 RESTATED]")
    void restated_whenCloseJumpsOverThreshold() {
        DomesticDailyPrice p = price(100, 110, 90, 100, 1000);

        assertThat(p.reconcile(50L, 55L, 45L, 50L, 2000L, false)).isEqualTo(CandleReconcileResult.RESTATED);
    }

    @Test
    @DisplayName("[상한가 30%는 재조정으로 오탐하지 않는다]")
    void upperLimit_isNotRestatement() {
        DomesticDailyPrice p = price(100, 110, 90, 100, 1000);

        assertThat(p.reconcile(100L, 130L, 100L, 130L, 2000L, false)).isEqualTo(CandleReconcileResult.UPDATED);
    }

    @Test
    @DisplayName("[정규장 종가가 확정된 행은 close를 유지하고 O/H/L/V만 갱신한다 - NXT 포함 종가로 덮어쓰지 않는다]")
    void confirmedRow_keepsClose() {
        DomesticDailyPrice p = price(100, 110, 90, 105, 0);
        p.confirmRegularClose(105L);

        CandleReconcileResult result = p.reconcile(101L, 112L, 89L, 999L, 1200L, false);

        assertThat(result).isEqualTo(CandleReconcileResult.UPDATED);
        assertThat(p.getClosePrice()).isEqualTo(105L);
        assertThat(p.getHighPrice()).isEqualTo(112L);
        assertThat(p.getVolume()).isEqualTo(1200L);
        assertThat(p.isRegularCloseConfirmed()).isTrue();
    }

    @Test
    @DisplayName("[확정 행은 close만 다르고 나머지가 같으면 UNCHANGED - close 차이는 무시한다]")
    void confirmedRow_ignoresCloseDifference() {
        DomesticDailyPrice p = price(100, 110, 90, 105, 1000);
        p.confirmRegularClose(105L);

        assertThat(p.reconcile(100L, 110L, 90L, 999L, 1000L, false)).isEqualTo(CandleReconcileResult.UNCHANGED);
    }

    @Test
    @DisplayName("[overwriteAll(수정주가 재백필)은 확정 행도 전체를 덮어쓰고 보호를 해제하며 재조정을 감지하지 않는다]")
    void overwriteAll_overridesProtection_withoutRestatementDetection() {
        DomesticDailyPrice p = price(100, 110, 90, 100, 1000);
        p.confirmRegularClose(100L);

        CandleReconcileResult result = p.reconcile(50L, 55L, 45L, 50L, 2000L, true);

        assertThat(result).isEqualTo(CandleReconcileResult.UPDATED);
        assertThat(p.getClosePrice()).isEqualTo(50L);
        assertThat(p.isRegularCloseConfirmed()).isFalse();
    }
}
