package com.quantlime.price.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.price.dto.PriceJumpReport;
import com.quantlime.price.util.PriceJumpDetector.PricePoint;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PriceJumpDetectorTest {

    private static final double THRESHOLD = 0.35;
    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);

    @Test
    @DisplayName("[임계값을 넘는 점프만 잡고, 리포트에 직전 거래일·종가·비율을 담는다]")
    void detect_reportsOnlyJumpsOverThreshold() {
        // given: 정상 -> 5:1 분할처럼 1/5토막 -> 정상
        List<PricePoint> points = List.of(
            new PricePoint(D1, 1000),
            new PricePoint(D1.plusDays(1), 1100),
            new PricePoint(D1.plusDays(2), 220),
            new PricePoint(D1.plusDays(3), 230));

        // when
        List<PriceJumpReport> jumps = PriceJumpDetector.detect("005930", points, THRESHOLD);

        // then
        assertThat(jumps).hasSize(1);
        PriceJumpReport jump = jumps.get(0);
        assertThat(jump.stockCode()).isEqualTo("005930");
        assertThat(jump.tradeDate()).isEqualTo(D1.plusDays(2));
        assertThat(jump.previousTradeDate()).isEqualTo(D1.plusDays(1));
        assertThat(jump.previousClose()).isEqualTo(1100);
        assertThat(jump.close()).isEqualTo(220);
        assertThat(jump.ratio()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("[상한가 수준(+30%) 변동과 점이 0~1개인 시계열은 빈 결과]")
    void detect_limitMoveAndShortSeries_returnEmpty() {
        assertThat(PriceJumpDetector.detect("A",
            List.of(new PricePoint(D1, 1000), new PricePoint(D1.plusDays(1), 1300)), THRESHOLD)).isEmpty();
        assertThat(PriceJumpDetector.detect("A", List.of(new PricePoint(D1, 1000)), THRESHOLD)).isEmpty();
        assertThat(PriceJumpDetector.detect("A", List.of(), THRESHOLD)).isEmpty();
    }

    @Test
    @DisplayName("[여러 번 점프하면 모두 시간순으로 보고한다]")
    void detect_multipleJumps_inOrder() {
        List<PricePoint> points = List.of(
            new PricePoint(D1, 1000),
            new PricePoint(D1.plusDays(1), 500),
            new PricePoint(D1.plusDays(2), 1000));

        List<PriceJumpReport> jumps = PriceJumpDetector.detect("A", points, THRESHOLD);

        assertThat(jumps).extracting(PriceJumpReport::tradeDate)
            .containsExactly(D1.plusDays(1), D1.plusDays(2));
    }
}
