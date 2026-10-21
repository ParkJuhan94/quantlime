package com.quantlime.videofeed.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.lang.Nullable;

public record VideoFeedDetailResponse(
    Long videoId,
    String channelName,
    String channelProfileImageUrl,
    String channelUrl,
    String title,
    @Nullable String videoUrl,
    LocalDateTime publishedAt,
    Integer durationSec,
    String summary,
    List<String> keyPoints,
    List<String> macroPoints,
    String caveat,
    List<VideoFeedTickerResponse> tickers
) {
}
