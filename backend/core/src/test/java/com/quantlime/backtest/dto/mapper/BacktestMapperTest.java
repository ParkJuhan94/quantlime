package com.quantlime.backtest.dto.mapper;

import com.quantlime.backtest.domain.BacktestAxis;
import com.quantlime.backtest.domain.BacktestResult;
import com.quantlime.backtest.dto.response.BacktestResponse;
import com.quantlime.backtest.dto.response.BacktestResponse.AxisBacktestResponse;
import com.quantlime.infra.python.dto.BacktestApiResponse;
import com.quantlime.infra.python.dto.BacktestApiResponse.AxisBacktestApiResponse;
import com.quantlime.infra.python.dto.BacktestApiResponse.BucketStatApiResponse;
import com.quantlime.infra.python.dto.BacktestApiResponse.HorizonStatApiResponse;
import com.quantlime.infra.python.dto.BacktestApiResponse.StabilityStatApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class BacktestMapperTest {

    @Test
    @DisplayName("[퀀트 엔진 응답(축 2 x horizon 2)을 (축, horizon)당 한 행씩 4개로 펼친다]")
    void toBacktestResults_flattensAxesAndHorizonsIntoFlatRows() {
        // given
        BacktestApiResponse apiResponse = new BacktestApiResponse(
            "005930", "v2.1", 300,
            List.of(
                new AxisBacktestApiResponse(
                    "trend",
                    List.of(
                        new HorizonStatApiResponse(5, 0.1, -0.1, 0.3, 300,
                            List.of(new BucketStatApiResponse(1, 0.01, 0.01, 0.5, 60))),
                        new HorizonStatApiResponse(10, 0.2, -0.05, 0.4, 290, List.of())),
                    new StabilityStatApiResponse(0.15, 0.2)),
                new AxisBacktestApiResponse(
                    "mean_reversion",
                    List.of(new HorizonStatApiResponse(5, -0.05, -0.2, 0.1, 300, List.of())),
                    new StabilityStatApiResponse(0.25, 0.3))
            )
        );

        // when
        List<BacktestResult> results = BacktestMapper.toBacktestResults(apiResponse, LocalDate.now());

        // then
        assertThat(results).hasSize(3);
        assertThat(results).extracting(BacktestResult::getAxis, BacktestResult::getHorizonDays)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(BacktestAxis.TREND, 5),
                org.assertj.core.groups.Tuple.tuple(BacktestAxis.TREND, 10),
                org.assertj.core.groups.Tuple.tuple(BacktestAxis.MEAN_REVERSION, 5));
        BacktestResult trend5 = results.stream()
            .filter(r -> r.getAxis() == BacktestAxis.TREND && r.getHorizonDays() == 5)
            .findFirst().orElseThrow();
        assertThat(trend5.getBuckets()).hasSize(1);
        assertThat(trend5.getScoreAutocorrelation()).isEqualTo(0.15);
    }

    @Test
    @DisplayName("[저장된 (축, horizon) 행들을 축 기준으로 다시 묶어 조회 응답으로 변환한다]")
    void toBacktestResponse_groupsFlatRowsBackByAxis() {
        // given: toBacktestResults의 역변환 시나리오
        BacktestResult trend5 = BacktestResult.of(
            "005930", BacktestAxis.TREND, 5, "v2.1", LocalDate.now(),
            300, 0.1, -0.1, 0.3, 0.15, 0.2, List.of());
        BacktestResult trend10 = BacktestResult.of(
            "005930", BacktestAxis.TREND, 10, "v2.1", LocalDate.now(),
            290, 0.2, -0.05, 0.4, 0.15, 0.2, List.of());
        BacktestResult meanReversion5 = BacktestResult.of(
            "005930", BacktestAxis.MEAN_REVERSION, 5, "v2.1", LocalDate.now(),
            300, -0.05, -0.2, 0.1, 0.25, 0.3, List.of());

        // when
        BacktestResponse response = BacktestMapper.toBacktestResponse(
            "005930", "v2.1", List.of(trend5, trend10, meanReversion5));

        // then
        assertThat(response.axes()).hasSize(2);
        AxisBacktestResponse trendAxis = response.axes().stream()
            .filter(a -> a.axis().equals("TREND")).findFirst().orElseThrow();
        assertThat(trendAxis.horizons()).extracting("horizonDays").containsExactly(5, 10);
        assertThat(trendAxis.scoreAutocorrelation()).isEqualTo(0.15);
    }
}
