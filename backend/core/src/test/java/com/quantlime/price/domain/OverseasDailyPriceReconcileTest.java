package com.quantlime.price.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class OverseasDailyPriceReconcileTest {

    private OverseasDailyPrice price(double close) {
        return OverseasDailyPrice.of("AAPL", LocalDate.of(2026, 10, 2), 100.0, 110.0, 90.0, close, 1000L);
    }

    @Test
    @DisplayName("[값이 그대로면 UNCHANGED]")
    void unchanged() {
        assertThat(price(100.0).reconcile(100.0, 110.0, 90.0, 100.0, 1000L, true))
            .isEqualTo(CandleReconcileResult.UNCHANGED);
    }

    @Test
    @DisplayName("[해외 임계값은 50% - 40% 변동은 UPDATED, 60% 변동은 RESTATED]")
    void restatementThreshold_isFiftyPercent() {
        assertThat(price(100.0).reconcile(100.0, 140.0, 90.0, 140.0, 1000L, true))
            .isEqualTo(CandleReconcileResult.UPDATED);
        assertThat(price(100.0).reconcile(100.0, 160.0, 90.0, 160.0, 1000L, true))
            .isEqualTo(CandleReconcileResult.RESTATED);
    }

    @Test
    @DisplayName("[detectRestatement=false(재백필 경로)면 큰 변동도 UPDATED]")
    void restatementDetectionOff() {
        assertThat(price(100.0).reconcile(100.0, 160.0, 90.0, 160.0, 1000L, false))
            .isEqualTo(CandleReconcileResult.UPDATED);
    }
}
