package com.quantlime.videofeed.dto;

public record CollectResult(
    String channelName,
    int discoveredCount,
    boolean success,
    String errorMessage
) {

    public static CollectResult success(String channelName, int discoveredCount) {
        return new CollectResult(channelName, discoveredCount, true, null);
    }

    public static CollectResult failed(String channelName, String errorMessage) {
        return new CollectResult(channelName, 0, false, errorMessage);
    }
}
