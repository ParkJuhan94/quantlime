package com.quantlime.backtest.dto.mapper;

import com.quantlime.backtest.domain.BacktestAxis;
import com.quantlime.backtest.domain.BacktestBucket;
import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.domain.BacktestSampleSplit;
import com.quantlime.backtest.domain.CrossSectionalBacktestResult;
import com.quantlime.infra.python.dto.BacktestApiRequest.OhlcvApiItem;
import com.quantlime.infra.python.dto.BacktestApiResponse.BucketStatApiResponse;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiRequest;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiRequest.DailyScorePointApiItem;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiRequest.StockDailyScoreApiItem;
import com.quantlime.infra.python.dto.CrossSectionalBacktestApiResponse;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.stock.domain.MarketType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class CrossSectionalBacktestMapper {

    // BacktestAxis.of(String)의 역방향 - 퀀트 엔진은 축을 snake_case 짧은
    // 코드("trend"/"mean_reversion")로 받는다.
    private static final Map<BacktestAxis, String> AXIS_CODE = Map.of(
        BacktestAxis.TREND, "trend",
        BacktestAxis.MEAN_REVERSION, "mean_reversion"
    );

    public static CrossSectionalBacktestApiRequest toApiRequest(
        MarketType market, String scoreVersion, Map<String, List<BacktestDailyScore>> dailyScoresByStock,
        List<BenchmarkIndex> benchmarkPrices, BacktestAxis axis, int horizonDays,
        boolean nullTest, int nullRepeats) {
        List<StockDailyScoreApiItem> stocks = dailyScoresByStock.entrySet().stream()
            .map(entry -> new StockDailyScoreApiItem(
                entry.getKey(),
                entry.getValue().stream().map(CrossSectionalBacktestMapper::toDailyScorePoint).toList()))
            .toList();
        return new CrossSectionalBacktestApiRequest(
            market.name(),
            scoreVersion,
            stocks,
            benchmarkPrices.stream().map(CrossSectionalBacktestMapper::toOhlcvApiItem).toList(),
            AXIS_CODE.get(axis),
            horizonDays,
            nullTest,
            nullRepeats
        );
    }

    private static DailyScorePointApiItem toDailyScorePoint(BacktestDailyScore dailyScore) {
        return new DailyScorePointApiItem(
            dailyScore.getTradeDate().toString(),
            dailyScore.getClosePrice(),
            dailyScore.getTrendScore(),
            dailyScore.getMeanReversionScore()
        );
    }

    private static OhlcvApiItem toOhlcvApiItem(BenchmarkIndex benchmarkIndex) {
        return new OhlcvApiItem(
            benchmarkIndex.getTradeDate().toString(),
            benchmarkIndex.getOpenPrice(),
            benchmarkIndex.getHighPrice(),
            benchmarkIndex.getLowPrice(),
            benchmarkIndex.getClosePrice(),
            0.0
        );
    }

    public static CrossSectionalBacktestResult toResult(
        MarketType market, BacktestAxis axis, BacktestSampleSplit sampleSplit,
        LocalDate backtestDate, CrossSectionalBacktestApiResponse response) {
        return CrossSectionalBacktestResult.of(
            market,
            axis,
            response.horizon(),
            response.scoreVersion(),
            sampleSplit,
            backtestDate,
            response.stockCount(),
            response.meanIc(),
            response.icCiLow(),
            response.icCiHigh(),
            response.nDates(),
            response.nObservations(),
            response.nullMean(),
            response.nullStd(),
            response.nullP2_5(),
            response.nullP97_5(),
            response.buckets().stream().map(CrossSectionalBacktestMapper::toBucket).toList()
        );
    }

    private static BacktestBucket toBucket(BucketStatApiResponse bucket) {
        return BacktestBucket.of(
            bucket.bucket(), bucket.meanExcessReturn(),
            bucket.medianExcessReturn(), bucket.hitRate(), bucket.sampleSize());
    }
}
