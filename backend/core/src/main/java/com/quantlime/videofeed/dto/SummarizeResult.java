package com.quantlime.videofeed.dto;

public record SummarizeResult(
    Long videoId,
    boolean success,
    String reason
) {

    public static SummarizeResult success(Long videoId) {
        return new SummarizeResult(videoId, true, null);
    }

    public static SummarizeResult failed(Long videoId, String reason) {
        return new SummarizeResult(videoId, false, reason);
    }
}
