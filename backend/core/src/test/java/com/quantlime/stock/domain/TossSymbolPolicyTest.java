package com.quantlime.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class TossSymbolPolicyTest {

    @Test
    @DisplayName("[10자 초과 심볼은 너무 길다고 판정한다]")
    void isTooLong() {
        assertThat(TossSymbolPolicy.isTooLong("ABCDEFGHIJ")).isFalse();
        assertThat(TossSymbolPolicy.isTooLong("ABCDEFGHIJK")).isTrue();
    }

    @Test
    @DisplayName("[영문·숫자·.,-만 Toss가 받는 형식이고 '/'가 섞인 SPAC 유닛·우선주 표기는 거부한다]")
    void isSupportedFormat() {
        assertThat(TossSymbolPolicy.isSupportedFormat("BRK.B")).isTrue();
        assertThat(TossSymbolPolicy.isSupportedFormat("AAPL")).isTrue();
        assertThat(TossSymbolPolicy.isSupportedFormat("AAC/UN")).isFalse();
        assertThat(TossSymbolPolicy.isSupportedFormat("ABR/F")).isFalse();
    }
}
