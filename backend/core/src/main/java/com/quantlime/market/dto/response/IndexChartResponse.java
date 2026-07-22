package com.quantlime.market.dto.response;

import java.time.LocalDate;

public record IndexChartResponse(
    LocalDate tradeDate,
    double open,
    double high,
    double low,
    double close
) {
}
