package com.quantlime.score.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

public record ScoreResponse(
    String stockCode,
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul") LocalDate scoreDate,
    Double trendScore,
    Double meanReversionScore,
    Double compositeScore,
    // ScoreRankingResponse와 동일한 이유(v3.0 백분위 도입) - 상세 화면은
    // 원점수·백분위를 함께 보여준다.
    Double trendPercentile,
    Double meanReversionPercentile,
    Double compositePercentile,
    String grade,
    String quadrant,
    Boolean divergenceFlag,
    String divergenceMessage,
    boolean insufficientData
) {
}
