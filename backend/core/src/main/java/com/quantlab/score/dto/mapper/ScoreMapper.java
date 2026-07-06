package com.quantlab.score.dto.mapper;

import com.quantlab.infra.python.dto.ScoreBatchApiResponse.StockScoreApiResponse;
import com.quantlab.score.domain.Grade;
import com.quantlab.score.domain.Score;
import com.quantlab.score.dto.response.ScoreRankingResponse;
import com.quantlab.score.dto.response.ScoreResponse;
import java.time.LocalDate;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class ScoreMapper {

    public static Score toScore(
        String stockCode, LocalDate scoreDate, StockScoreApiResponse apiResponse) {
        return Score.of(
            stockCode,
            scoreDate,
            apiResponse.trendScore(),
            apiResponse.meanReversionScore(),
            apiResponse.compositeScore(),
            extractGrade(apiResponse),
            extractDivergenceFlag(apiResponse),
            extractDivergenceMessage(apiResponse),
            apiResponse.comment(),
            apiResponse.insufficientData()
        );
    }

    public static void updateScoreFrom(Score score, StockScoreApiResponse apiResponse) {
        score.updateFrom(
            apiResponse.trendScore(),
            apiResponse.meanReversionScore(),
            apiResponse.compositeScore(),
            extractGrade(apiResponse),
            extractDivergenceFlag(apiResponse),
            extractDivergenceMessage(apiResponse),
            apiResponse.comment(),
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
            score.getDivergenceFlag(),
            score.getDivergenceMessage(),
            score.getComment(),
            score.isInsufficientData()
        );
    }

    public static ScoreRankingResponse toScoreRankingResponse(Score score, String stockName) {
        return new ScoreRankingResponse(
            score.getStockCode(),
            stockName,
            score.getScoreDate(),
            score.getTrendScore(),
            score.getMeanReversionScore(),
            score.getCompositeScore(),
            gradeLabel(score.getGrade()),
            score.isInsufficientData()
        );
    }

    private static Grade extractGrade(StockScoreApiResponse apiResponse) {
        return apiResponse.grade() != null ? Grade.of(apiResponse.grade()) : null;
    }

    private static Boolean extractDivergenceFlag(StockScoreApiResponse apiResponse) {
        return apiResponse.divergence() != null ? apiResponse.divergence().flag() : null;
    }

    private static String extractDivergenceMessage(StockScoreApiResponse apiResponse) {
        return apiResponse.divergence() != null ? apiResponse.divergence().message() : null;
    }

    private static String gradeLabel(Grade grade) {
        return grade != null ? grade.getLabel() : null;
    }
}
