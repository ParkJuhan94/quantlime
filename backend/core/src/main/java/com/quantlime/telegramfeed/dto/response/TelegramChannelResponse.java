package com.quantlime.telegramfeed.dto.response;

import com.quantlime.videofeed.domain.TelegramFilterConfig;
import java.time.LocalDateTime;

public record TelegramChannelResponse(
    Long channelId,
    String externalChannelId,
    String channelUrl,
    String name,
    boolean enabled,
    int priority,
    TelegramFilterConfig filterConfig,
    LocalDateTime lastCollectedAt,
    String profileImageUrl
) {
}
