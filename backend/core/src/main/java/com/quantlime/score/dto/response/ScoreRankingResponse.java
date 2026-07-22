package com.quantlime.score.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

public record ScoreRankingResponse(
    String stockCode,
    String stockName,
    String sector,
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul") LocalDate scoreDate,
    Double trendScore,
    Double meanReversionScore,
    Double compositeScore,
    String grade,
    boolean insufficientData
) {
}
