package com.quantlime.infra.telegram.dto;

import java.time.LocalDateTime;
import org.springframework.lang.Nullable;

public record TelegramPreviewMessage(
    String externalPostId,
    long messageId,
    String content,
    LocalDateTime publishedAt,
    @Nullable Long viewCount,
    boolean hasMedia
) {
}
