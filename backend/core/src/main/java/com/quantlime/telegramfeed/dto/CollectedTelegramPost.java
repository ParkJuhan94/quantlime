package com.quantlime.telegramfeed.dto;

import java.time.LocalDateTime;

public record CollectedTelegramPost(
    String externalPostId,
    long messageId,
    String content,
    LocalDateTime publishedAt,
    Long viewCount,
    boolean hasMedia
) {
}
