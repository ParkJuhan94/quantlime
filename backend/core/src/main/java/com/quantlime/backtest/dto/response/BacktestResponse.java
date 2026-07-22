package com.quantlime.backtest.dto.response;

import java.time.LocalDate;
import java.util.List;

public record BacktestResponse(
    String stockCode,
    String scoreVersion,
    LocalDate backtestDate,
    List<AxisBacktestResponse> axes
) {

    public record AxisBacktestResponse(
        String axis,
        String axisLabel,
        Double scoreAutocorrelation,
        Double gradeFlipRate,
        List<HorizonBacktestResponse> horizons
    ) {
    }

    public record HorizonBacktestResponse(
        int horizonDays,
        Double rankIc,
        Double rankIcCiLow,
        Double rankIcCiHigh,
        int sampleSize,
        List<BucketResponse> buckets
    ) {
    }

    public record BucketResponse(
        int bucketNumber,
        Double meanExcessReturn,
        Double medianExcessReturn,
        Double hitRate,
        int sampleSize
    ) {
    }
}
