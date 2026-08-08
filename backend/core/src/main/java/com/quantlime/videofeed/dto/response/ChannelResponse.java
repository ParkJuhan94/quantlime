package com.quantlime.videofeed.dto.response;

import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChannelResponse(
    Long channelId,
    Platform platform,
    String externalChannelId,
    String channelUrl,
    String name,
    boolean enabled,
    int priority,
    ChannelFilterConfig filterConfig,
    BigDecimal medianVelocity,
    LocalDateTime lastCollectedAt,
    String profileImageUrl
) {
}
