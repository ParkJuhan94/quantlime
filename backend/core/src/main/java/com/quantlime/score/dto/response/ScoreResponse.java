package com.quantlime.score.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import org.springframework.lang.Nullable;

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
    @Nullable String grade,
    @Nullable String quadrant,
    @Nullable Boolean divergenceFlag,
    @Nullable String divergenceMessage,
    boolean insufficientData
) {
}
