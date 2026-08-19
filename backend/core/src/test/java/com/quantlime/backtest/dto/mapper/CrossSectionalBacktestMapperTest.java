package com.quantlime.backtest.dto.mapper;

import com.quantlime.backtest.domain.BacktestAxis;
import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.domain.BacktestSampleSplit;
import com.quantlime.backtest.domain.CrossSectionalBacktestResult;
import com.quantlime.infra.python.dto.BacktestApiResponse.BucketStatApiResponse;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiRequest;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiResponse;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.stock.domain.MarketType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class CrossSectionalBacktestMapperTest {

    @Test
    @DisplayName("[여러 종목의 일별 스코어를 (축, horizon) 하나에 대한 API 요청으로 변환한다]")
    void toApiRequest_buildsRequestWithAxisCodeAndSingleHorizon() {
        // given
        Map<String, List<BacktestDailyScore>> dailyScoresByStock = Map.of(
            "005930", List.of(dailyScore("005930")));
        List<BenchmarkIndex> benchmarkPrices = List.of(
            BenchmarkIndex.of("KOSPI", LocalDate.now(), 2600.0, 2610.0, 2590.0, 2605.0));

        // when
        CrossSectionalBacktestApiRequest request = CrossSectionalBacktestMapper.toApiRequest(
            MarketType.KOSPI, "v2.1", dailyScoresByStock, benchmarkPrices,
            BacktestAxis.MEAN_REVERSION, 20, true, 200);

        // then: 파이썬 엔진은 snake_case 축 코드("mean_reversion")를 받는다
        assertThat(request.market()).isEqualTo("KOSPI");
        assertThat(request.axis()).isEqualTo("mean_reversion");
        assertThat(request.horizon()).isEqualTo(20);
        assertThat(request.nullTest()).isTrue();
        assertThat(request.nullRepeats()).isEqualTo(200);
        assertThat(request.stocks()).hasSize(1);
        assertThat(request.stocks().get(0).stockCode()).isEqualTo("005930");
        assertThat(request.stocks().get(0).dailyScores()).hasSize(1);
        assertThat(request.benchmarkOhlcv()).hasSize(1);
    }

    @Test
    @DisplayName("[API 응답을 CrossSectionalBacktestResult 엔티티로 변환한다]")
    void toResult_convertsApiResponseToEntity() {
        // given
        CrossSectionalBacktestApiResponse response = new CrossSectionalBacktestApiResponse(
            "KOSPI", "v2.1", 250, "trend", 20,
            -0.03, -0.07, 0.01, 340, 85000,
            List.of(new BucketStatApiResponse(1, 0.01, 0.008, 0.51, 17000)),
            -0.005, 0.02, -0.04, 0.03);

        // when
        CrossSectionalBacktestResult result = CrossSectionalBacktestMapper.toResult(
            MarketType.KOSPI, BacktestAxis.TREND, BacktestSampleSplit.FULL, LocalDate.now(), response);

        // then
        assertThat(result.getMarketType()).isEqualTo(MarketType.KOSPI);
        assertThat(result.getAxis()).isEqualTo(BacktestAxis.TREND);
        assertThat(result.getHorizonDays()).isEqualTo(20);
        assertThat(result.getSampleSplit()).isEqualTo(BacktestSampleSplit.FULL);
        assertThat(result.getMeanIc()).isEqualTo(-0.03);
        assertThat(result.getSampleDates()).isEqualTo(340);
        assertThat(result.getSampleObservations()).isEqualTo(85000);
        assertThat(result.getNullMean()).isEqualTo(-0.005);
        assertThat(result.getBuckets()).hasSize(1);
        assertThat(result.getBuckets().get(0).getSampleSize()).isEqualTo(17000);
    }

    private BacktestDailyScore dailyScore(String stockCode) {
        return BacktestDailyScore.of(
            stockCode, "v2.1", LocalDate.now(), 70000.0, 60.0, 55.0, null, null);
    }
}
