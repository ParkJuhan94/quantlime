package com.quantlab.infra.python.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ScoreBatchApiResponse(
    List<StockScoreApiResponse> scores
) {

    public record StockScoreApiResponse(
        @JsonProperty("stock_code") String stockCode,
        @JsonProperty("trend_score") Double trendScore,
        @JsonProperty("mean_reversion_score") Double meanReversionScore,
        @JsonProperty("composite_score") Double compositeScore,
        String grade,
        DivergenceApiResponse divergence,
        String comment,
        @JsonProperty("insufficient_data") boolean insufficientData
    ) {
    }

    public record DivergenceApiResponse(
        boolean flag,
        String message
    ) {
    }
}
