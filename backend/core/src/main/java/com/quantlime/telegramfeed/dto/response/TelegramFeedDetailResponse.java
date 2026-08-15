package com.quantlime.telegramfeed.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record TelegramFeedDetailResponse(
    Long telegramPostId,
    String channelName,
    String channelProfileImageUrl,
    String channelUrl,
    String postUrl,
    LocalDateTime publishedAt,
    Long viewCount,
    String content,
    String summary,
    List<String> keyPoints,
    List<String> macroPoints,
    String caveat,
    List<TelegramFeedTickerResponse> tickers
) {
}
