package com.quantlime.telegramfeed.dto;

// videofeed.dto.SummarizeResult와 구조적으로 동일(Phase 8 P7-4).
public record TelegramSummarizeResult(
    Long telegramPostId,
    boolean success,
    String reason
) {

    public static TelegramSummarizeResult success(Long telegramPostId) {
        return new TelegramSummarizeResult(telegramPostId, true, null);
    }

    public static TelegramSummarizeResult failed(Long telegramPostId, String reason) {
        return new TelegramSummarizeResult(telegramPostId, false, reason);
    }
}
