package com.quantlime.videofeed.dto;

import java.time.LocalDateTime;

public record CollectedVideo(
    String externalVideoId,
    String title,
    LocalDateTime publishedAt,
    Integer durationSec,
    Long viewCount
) {
}
