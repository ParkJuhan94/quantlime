package com.quantlime.telegramfeed.dto;

public record TelegramCollectResult(
    String channelName,
    int discoveredCount,
    boolean success,
    String errorMessage
) {

    public static TelegramCollectResult success(String channelName, int discoveredCount) {
        return new TelegramCollectResult(channelName, discoveredCount, true, null);
    }

    public static TelegramCollectResult failed(String channelName, String errorMessage) {
        return new TelegramCollectResult(channelName, 0, false, errorMessage);
    }
}
