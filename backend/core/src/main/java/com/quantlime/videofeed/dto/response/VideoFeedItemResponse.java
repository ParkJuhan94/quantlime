package com.quantlime.videofeed.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.lang.Nullable;

public record VideoFeedItemResponse(
    Long videoId,
    String channelName,
    String channelProfileImageUrl,
    String channelUrl,
    String title,
    @Nullable String videoUrl,
    LocalDateTime publishedAt,
    Integer durationSec,
    String summary,
    List<VideoFeedTickerResponse> tickers
) {
}
