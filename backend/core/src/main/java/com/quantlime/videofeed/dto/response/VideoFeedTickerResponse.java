package com.quantlime.videofeed.dto.response;

import java.math.BigDecimal;

public record VideoFeedTickerResponse(
    String tickerCode,
    String tickerName,
    String stance,
    BigDecimal confidence
) {
}
