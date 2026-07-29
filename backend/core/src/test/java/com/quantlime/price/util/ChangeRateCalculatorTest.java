package com.quantlime.price.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

@Tag("unit")
class ChangeRateCalculatorTest {

    @Test
    @DisplayName("[전일 종가 대비 등락률을 퍼센트 숫자로 계산한다]")
    void calculate_returnsPercentageNotFraction() {
        // when: 100 -> 105는 5% 상승
        Double result = ChangeRateCalculator.calculate(105.0, 100.0);

        // then: 0.05가 아니라 5.0
        assertThat(result).isCloseTo(5.0, offset(0.0001));
    }

    @Test
    @DisplayName("[하락 시 음수 등락률을 반환한다]")
    void calculate_priceDown_returnsNegativeRate() {
        // when
        Double result = ChangeRateCalculator.calculate(90.0, 100.0);

        // then
        assertThat(result).isCloseTo(-10.0, offset(0.0001));
    }

    @Test
    @DisplayName("[전일 종가가 없으면 null을 반환한다]")
    void calculate_previousCloseNull_returnsNull() {
        assertThat(ChangeRateCalculator.calculate(100.0, null)).isNull();
    }

    @Test
    @DisplayName("[전일 종가가 0이면 null을 반환한다]")
    void calculate_previousCloseZero_returnsNull() {
        assertThat(ChangeRateCalculator.calculate(100.0, 0.0)).isNull();
    }
}
