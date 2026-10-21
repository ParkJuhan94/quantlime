package com.quantlime.videofeed.dto;

import java.time.LocalDateTime;
import org.springframework.lang.Nullable;

public record CollectedVideo(
    String externalVideoId,
    String title,
    LocalDateTime publishedAt,
    @Nullable Integer durationSec,
    @Nullable Long viewCount
) {
}
