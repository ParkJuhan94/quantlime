package com.quantlab.score.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

public record ScoreResponse(
    String stockCode,
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul") LocalDate scoreDate,
    Double trendScore,
    Double meanReversionScore,
    Double compositeScore,
    String grade,
    Boolean divergenceFlag,
    String divergenceMessage,
    String comment,
    boolean insufficientData
) {
}
