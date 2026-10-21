package com.quantlime.telegramfeed.dto;

import java.time.LocalDateTime;
import org.springframework.lang.Nullable;

public record CollectedTelegramPost(
    String externalPostId,
    long messageId,
    String content,
    LocalDateTime publishedAt,
    @Nullable Long viewCount,
    boolean hasMedia
) {
}
