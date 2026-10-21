package com.quantlime.videofeed.dto;

import org.springframework.lang.Nullable;

public record CollectResult(
    String channelName,
    int discoveredCount,
    boolean success,
    @Nullable String errorMessage
) {

    public static CollectResult success(String channelName, int discoveredCount) {
        return new CollectResult(channelName, discoveredCount, true, null);
    }

    public static CollectResult failed(String channelName, @Nullable String errorMessage) {
        return new CollectResult(channelName, 0, false, errorMessage);
    }
}
