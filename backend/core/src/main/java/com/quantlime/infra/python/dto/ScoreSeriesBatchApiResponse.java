package com.quantlime.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 종목별 날짜별 스코어 시계열 응답(/calculate/score/series). 종목 하나당
 * OHLCV 전 구간 기준 스코어가 한 번에 통째로 내려오므로, "오늘자 갱신"과
 * "과거 결측 이력 채우기"가 이 응답 하나로 전부 처리된다(ScoreService 참고).
 */
public record ScoreSeriesBatchApiResponse(
    List<StockScoreSeriesApiResponse> scores
) {

    public record StockScoreSeriesApiResponse(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("daily_scores") List<DailyScoreSeriesApiResponse> dailyScores
    ) {
    }

    // date를 String으로 두는 이유는 다른 python DTO들과 동일(PythonEngineConfig
    // 주석 참고 - RestClient 기본 컨버터가 LocalDate를 배열로 다루는 문제).
    public record DailyScoreSeriesApiResponse(
        String date,
        @JsonProperty("trend_score") Double trendScore,
        @JsonProperty("mean_reversion_score") Double meanReversionScore,
        @JsonProperty("composite_score") Double compositeScore,
        String grade,
        String quadrant,
        DivergenceApiResponse divergence,
        @JsonProperty("insufficient_data") boolean insufficientData
    ) {
    }

    public record DivergenceApiResponse(
        boolean flag,
        String message
    ) {
    }
}
