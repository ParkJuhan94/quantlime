package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BacktestApiResponse(
    @JsonProperty("stock_code") String stockCode,
    @JsonProperty("score_version") String scoreVersion,
    @JsonProperty("sample_days") int sampleDays,
    List<AxisBacktestApiResponse> axes
) {

    public record AxisBacktestApiResponse(
        String axis,
        List<HorizonStatApiResponse> horizons,
        StabilityStatApiResponse stability
    ) {
    }

    public record HorizonStatApiResponse(
        int horizon,
        @JsonProperty("rank_ic") Double rankIc,
        @JsonProperty("rank_ic_ci_low") Double rankIcCiLow,
        @JsonProperty("rank_ic_ci_high") Double rankIcCiHigh,
        @JsonProperty("sample_size") int sampleSize,
        List<BucketStatApiResponse> buckets
    ) {
    }

    public record BucketStatApiResponse(
        int bucket,
        @JsonProperty("mean_excess_return") Double meanExcessReturn,
        @JsonProperty("median_excess_return") Double medianExcessReturn,
        @JsonProperty("hit_rate") Double hitRate,
        @JsonProperty("sample_size") int sampleSize
    ) {
    }

    public record StabilityStatApiResponse(
        @JsonProperty("score_autocorrelation") Double scoreAutocorrelation,
        @JsonProperty("grade_flip_rate") Double gradeFlipRate
    ) {
    }
}
