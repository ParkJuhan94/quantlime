package com.quantlime.telegramfeed.dto.response;

import java.math.BigDecimal;

// videofeed.dto.response.VideoFeedTickerResponse와 구조적으로 동일.
public record TelegramFeedTickerResponse(
    String tickerCode,
    String tickerName,
    String stance,
    BigDecimal confidence
) {
}
