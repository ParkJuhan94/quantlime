package com.quantlime.backtest.dto.response;

import java.time.LocalDate;
import java.util.List;

public record BacktestResponse(
    String stockCode,
    String scoreVersion,
    LocalDate backtestDate,
    List<AxisBacktestResponse> axes,
    List<DailyScoreResponse> dailyScores
) {

    // 가격차트 오버레이·사분면 배경밴드·스코어 분포 히스토그램(Phase F)용
    // 일별 원시 스코어. quadrant/grade는 ScoreResponse와 동일하게 enum
    // 이름이 아니라 한글 표시명으로 내려온다(null 가능 - 워밍업 구간).
    public record DailyScoreResponse(
        LocalDate tradeDate,
        Double closePrice,
        Double trendScore,
        Double meanReversionScore,
        String quadrant,
        String grade
    ) {
    }

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
