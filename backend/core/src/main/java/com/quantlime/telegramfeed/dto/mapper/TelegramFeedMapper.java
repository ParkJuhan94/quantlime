package com.quantlime.telegramfeed.dto.mapper;

import com.quantlime.telegramfeed.dto.response.TelegramChannelResponse;
import com.quantlime.videofeed.domain.Channel;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class TelegramFeedMapper {

    public static TelegramChannelResponse toChannelResponse(Channel channel) {
        return new TelegramChannelResponse(
            channel.getId(),
            channel.getExternalChannelId(),
            toChannelUrl(channel),
            channel.getName(),
            channel.isEnabled(),
            channel.getPriority(),
            channel.getTelegramFilterConfig(),
            channel.getLastCollectedAt(),
            channel.getProfileImageUrl());
    }

    private static String toChannelUrl(Channel channel) {
        return "https://t.me/" + channel.getExternalChannelId();
    }
}
