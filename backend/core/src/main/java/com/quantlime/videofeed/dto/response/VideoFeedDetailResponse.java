package com.quantlime.videofeed.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record VideoFeedDetailResponse(
    Long videoId,
    String channelName,
    String channelProfileImageUrl,
    String channelUrl,
    String title,
    String videoUrl,
    LocalDateTime publishedAt,
    Integer durationSec,
    String summary,
    List<String> keyPoints,
    String caveat,
    List<VideoFeedTickerResponse> tickers
) {
}
