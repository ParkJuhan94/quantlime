package com.quantlime.score.dto.mapper;

import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse.DailyScoreSeriesApiResponse;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse.DivergenceApiResponse;
import com.quantlime.score.domain.Divergence;
import com.quantlime.score.domain.Grade;
import com.quantlime.score.domain.Quadrant;
import com.quantlime.score.domain.Score;
import com.quantlime.score.dto.response.ScoreRankingResponse;
import com.quantlime.score.dto.response.ScoreResponse;
import java.time.LocalDate;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class ScoreMapper {

    public static Score toScore(
        String stockCode, LocalDate scoreDate, DailyScoreSeriesApiResponse apiResponse) {
        return Score.of(
            stockCode,
            scoreDate,
            apiResponse.trendScore(),
            apiResponse.meanReversionScore(),
            apiResponse.compositeScore(),
            extractGrade(apiResponse),
            extractQuadrant(apiResponse),
            extractDivergence(apiResponse),
            apiResponse.insufficientData()
        );
    }

    public static void updateScoreFrom(Score score, DailyScoreSeriesApiResponse apiResponse) {
        score.updateFrom(
            apiResponse.trendScore(),
            apiResponse.meanReversionScore(),
            apiResponse.compositeScore(),
            extractGrade(apiResponse),
            extractQuadrant(apiResponse),
            extractDivergence(apiResponse),
            apiResponse.insufficientData()
        );
    }

    public static ScoreResponse toScoreResponse(Score score) {
        return new ScoreResponse(
            score.getStockCode(),
            score.getScoreDate(),
            score.getTrendScore(),
            score.getMeanReversionScore(),
            score.getCompositeScore(),
            gradeLabel(score.getGrade()),
            quadrantLabel(score.getQuadrant()),
            divergenceFlag(score.getDivergence()),
            divergenceMessage(score.getDivergence()),
            score.isInsufficientData()
        );
    }

    public static ScoreRankingResponse toScoreRankingResponse(Score score, String stockName, String sector) {
        return new ScoreRankingResponse(
            score.getStockCode(),
            stockName,
            sector,
            score.getScoreDate(),
            score.getTrendScore(),
            score.getMeanReversionScore(),
            score.getCompositeScore(),
            gradeLabel(score.getGrade()),
            score.isInsufficientData()
        );
    }

    private static Grade extractGrade(DailyScoreSeriesApiResponse apiResponse) {
        return apiResponse.grade() != null ? Grade.of(apiResponse.grade()) : null;
    }

    private static Quadrant extractQuadrant(DailyScoreSeriesApiResponse apiResponse) {
        return apiResponse.quadrant() != null ? Quadrant.of(apiResponse.quadrant()) : null;
    }

    private static Divergence extractDivergence(DailyScoreSeriesApiResponse apiResponse) {
        DivergenceApiResponse divergence = apiResponse.divergence();
        return divergence != null ? Divergence.of(divergence.flag(), divergence.message()) : null;
    }

    private static String gradeLabel(Grade grade) {
        return grade != null ? grade.getLabel() : null;
    }

    private static String quadrantLabel(Quadrant quadrant) {
        return quadrant != null ? quadrant.getLabel() : null;
    }

    private static Boolean divergenceFlag(Divergence divergence) {
        return divergence != null ? divergence.getFlag() : null;
    }

    private static String divergenceMessage(Divergence divergence) {
        return divergence != null ? divergence.getMessage() : null;
    }
}
