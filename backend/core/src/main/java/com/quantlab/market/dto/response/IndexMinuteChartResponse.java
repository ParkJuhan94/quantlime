package com.quantlab.market.dto.response;

import java.time.LocalDateTime;

public record IndexMinuteChartResponse(
    LocalDateTime time,
    double price
) {
}
