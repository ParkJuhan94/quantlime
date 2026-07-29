package com.quantlime.videofeed.dto;

public record TranscribeResult(
    Long videoId,
    boolean success,
    boolean available,
    String reason
) {

    public static TranscribeResult success(Long videoId) {
        return new TranscribeResult(videoId, true, true, null);
    }

    public static TranscribeResult unavailable(Long videoId, String reason) {
        return new TranscribeResult(videoId, true, false, reason);
    }

    public static TranscribeResult failed(Long videoId, String reason) {
        return new TranscribeResult(videoId, false, false, reason);
    }
}
