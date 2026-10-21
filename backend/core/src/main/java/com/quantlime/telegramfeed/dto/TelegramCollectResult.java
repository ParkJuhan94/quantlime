package com.quantlime.telegramfeed.dto;

import org.springframework.lang.Nullable;

public record TelegramCollectResult(
    String channelName,
    int discoveredCount,
    boolean success,
    @Nullable String errorMessage
) {

    public static TelegramCollectResult success(String channelName, int discoveredCount) {
        return new TelegramCollectResult(channelName, discoveredCount, true, null);
    }

    public static TelegramCollectResult failed(String channelName, @Nullable String errorMessage) {
        return new TelegramCollectResult(channelName, 0, false, errorMessage);
    }
}
