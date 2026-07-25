package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BacktestApiResponse(
    @JsonProperty("stock_code") String stockCode,
    @JsonProperty("score_version") String scoreVersion,
    @JsonProperty("sample_days") int sampleDays,
    List<AxisBacktestApiResponse> axes,
    @JsonProperty("daily_scores") List<DailyScoreApiResponse> dailyScores
) {

    // date를 String으로 두는 이유는 다른 python DTO들과 동일(PythonEngineConfig
    // 주석 참고 - RestClient 기본 컨버터가 LocalDate를 배열로 다루는 문제).
    // 엔진의 DailyScoreResponse가 스코어 시계열 백필(ScoreSeriesBatchApiResponse)
    // 용으로 composite_score/divergence/insufficient_data 필드를 추가로
    // 갖게 됐는데, 이 DTO는 그 필드들을 쓰지 않으므로 ignoreUnknown으로
    // 안전하게 무시한다(안 붙이면 Jackson 기본값이 미지 필드에서 역직렬화
    // 자체를 실패시킨다).
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyScoreApiResponse(
        String date,
        double close,
        @JsonProperty("trend_score") Double trendScore,
        @JsonProperty("mean_reversion_score") Double meanReversionScore,
        String quadrant,
        String grade
    ) {
    }

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
