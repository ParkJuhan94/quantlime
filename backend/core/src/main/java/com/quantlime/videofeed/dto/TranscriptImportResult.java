package com.quantlime.videofeed.dto;

public record TranscriptImportResult(
    String externalVideoId,
    Outcome outcome,
    String detail
) {

    public enum Outcome {
        IMPORTED,
        ALREADY_DONE,
        NOT_FOUND,
        INVALID_STATUS
    }

    public static TranscriptImportResult imported(String externalVideoId) {
        return new TranscriptImportResult(externalVideoId, Outcome.IMPORTED, null);
    }

    public static TranscriptImportResult alreadyDone(String externalVideoId, String status) {
        return new TranscriptImportResult(externalVideoId, Outcome.ALREADY_DONE, "이미 " + status + " 상태");
    }

    public static TranscriptImportResult notFound(String externalVideoId) {
        return new TranscriptImportResult(externalVideoId, Outcome.NOT_FOUND, "운영 DB에 해당 영상이 없음(아직 수집 안 됨)");
    }

    public static TranscriptImportResult invalidStatus(String externalVideoId, String status) {
        return new TranscriptImportResult(externalVideoId, Outcome.INVALID_STATUS, "자막을 받을 수 없는 상태: " + status);
    }
}
