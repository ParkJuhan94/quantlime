package com.quantlime.infra.telegram.dto;

import java.time.LocalDateTime;

public record TelegramPreviewMessage(
    String externalPostId,
    long messageId,
    String content,
    LocalDateTime publishedAt,
    Long viewCount,
    boolean hasMedia
) {
}
