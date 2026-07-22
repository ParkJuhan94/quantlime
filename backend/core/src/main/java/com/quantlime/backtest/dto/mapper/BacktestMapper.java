package com.quantlime.backtest.dto.mapper;

import com.quantlime.backtest.domain.BacktestAxis;
import com.quantlime.backtest.domain.BacktestBucket;
import com.quantlime.backtest.domain.BacktestResult;
import com.quantlime.backtest.dto.response.BacktestResponse;
import com.quantlime.backtest.dto.response.BacktestResponse.AxisBacktestResponse;
import com.quantlime.backtest.dto.response.BacktestResponse.BucketResponse;
import com.quantlime.backtest.dto.response.BacktestResponse.HorizonBacktestResponse;
import com.quantlime.infra.python.dto.BacktestApiResponse;
import com.quantlime.infra.python.dto.BacktestApiResponse.AxisBacktestApiResponse;
import com.quantlime.infra.python.dto.BacktestApiResponse.BucketStatApiResponse;
import com.quantlime.infra.python.dto.BacktestApiResponse.HorizonStatApiResponse;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class BacktestMapper {

    /**
     * 퀀트 엔진 응답(축 2개 x horizon 4개)을 (축, horizon) 조합당 한 행인
     * {@link BacktestResult} 8개로 펼친다 - 안정성 지표(자기상관/등급
     * 플립률)는 축 단위라 같은 축의 4개 horizon 행에 그대로 중복 저장한다
     * (BacktestResult 클래스 주석 참고).
     */
    public static List<BacktestResult> toBacktestResults(
        BacktestApiResponse apiResponse, LocalDate backtestDate) {
        return apiResponse.axes().stream()
            .flatMap(axisResponse -> axisResponse.horizons().stream()
                .map(horizon -> toBacktestResult(
                    apiResponse.stockCode(), apiResponse.scoreVersion(),
                    backtestDate, axisResponse, horizon)))
            .toList();
    }

    private static BacktestResult toBacktestResult(
        String stockCode, String scoreVersion, LocalDate backtestDate,
        AxisBacktestApiResponse axisResponse, HorizonStatApiResponse horizon) {
        return BacktestResult.of(
            stockCode,
            BacktestAxis.of(axisResponse.axis()),
            horizon.horizon(),
            scoreVersion,
            backtestDate,
            horizon.sampleSize(),
            horizon.rankIc(),
            horizon.rankIcCiLow(),
            horizon.rankIcCiHigh(),
            axisResponse.stability().scoreAutocorrelation(),
            axisResponse.stability().gradeFlipRate(),
            horizon.buckets().stream().map(BacktestMapper::toBucket).toList()
        );
    }

    private static BacktestBucket toBucket(BucketStatApiResponse bucket) {
        return BacktestBucket.of(
            bucket.bucket(), bucket.meanExcessReturn(),
            bucket.medianExcessReturn(), bucket.hitRate(), bucket.sampleSize());
    }

    /**
     * 저장된 (축, horizon) 행들을 축 기준으로 묶어 조회 응답으로 재구성한다 -
     * toBacktestResults가 펼친 것의 역변환.
     */
    public static BacktestResponse toBacktestResponse(
        String stockCode, String scoreVersion, List<BacktestResult> results) {
        LocalDate backtestDate = results.stream()
            .map(BacktestResult::getBacktestDate)
            .max(Comparator.naturalOrder())
            .orElse(null);

        Map<BacktestAxis, List<BacktestResult>> byAxis = results.stream()
            .collect(Collectors.groupingBy(BacktestResult::getAxis));

        List<AxisBacktestResponse> axes = byAxis.entrySet().stream()
            .map(entry -> toAxisResponse(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(AxisBacktestResponse::axis))
            .toList();

        return new BacktestResponse(stockCode, scoreVersion, backtestDate, axes);
    }

    private static AxisBacktestResponse toAxisResponse(BacktestAxis axis, List<BacktestResult> results) {
        BacktestResult anyResult = results.get(0);
        List<HorizonBacktestResponse> horizons = results.stream()
            .sorted(Comparator.comparingInt(BacktestResult::getHorizonDays))
            .map(BacktestMapper::toHorizonResponse)
            .toList();
        return new AxisBacktestResponse(
            axis.name(),
            axis.getLabel(),
            anyResult.getScoreAutocorrelation(),
            anyResult.getGradeFlipRate(),
            horizons
        );
    }

    private static HorizonBacktestResponse toHorizonResponse(BacktestResult result) {
        List<BucketResponse> buckets = result.getBuckets().stream()
            .sorted(Comparator.comparingInt(BacktestBucket::getBucketNumber))
            .map(bucket -> new BucketResponse(
                bucket.getBucketNumber(), bucket.getMeanExcessReturn(),
                bucket.getMedianExcessReturn(), bucket.getHitRate(), bucket.getSampleSize()))
            .toList();
        return new HorizonBacktestResponse(
            result.getHorizonDays(), result.getRankIc(), result.getRankIcCiLow(),
            result.getRankIcCiHigh(), result.getSampleSize(), buckets);
    }
}
