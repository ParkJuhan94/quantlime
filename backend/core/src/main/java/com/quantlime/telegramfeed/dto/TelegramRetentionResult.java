package com.quantlime.telegramfeed.dto;

// 2026-08-15 다이제스트 재설계 이후 보존기간 정리가 TelegramPost/
// TelegramDigest 두 테이블을 함께 정리하므로 단일 int 대신 각 삭제건수를
// 둘 다 보고한다.
public record TelegramRetentionResult(
    int deletedPostCount,
    int deletedDigestCount
) {
}
