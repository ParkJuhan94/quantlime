package com.quantlab.price.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

public record DailyChartResponse(
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul") LocalDate tradeDate,
    Long open,
    Long high,
    Long low,
    Long close,
    Long volume
) {
}
