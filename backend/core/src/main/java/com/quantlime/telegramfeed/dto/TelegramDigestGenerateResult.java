package com.quantlime.telegramfeed.dto;

public record TelegramDigestGenerateResult(
    String channelName,
    int sourcePostCount,
    boolean success,
    String reason
) {

    public static TelegramDigestGenerateResult success(String channelName, int sourcePostCount) {
        return new TelegramDigestGenerateResult(channelName, sourcePostCount, true, null);
    }

    // 그날 SELECTED된 글이 하나도 없어 다이제스트를 만들 재료 자체가 없는
    // 정상 상황 - 실패가 아니라 스킵이다.
    public static TelegramDigestGenerateResult skipped(String channelName) {
        return new TelegramDigestGenerateResult(channelName, 0, true, "NO_ELIGIBLE_POSTS");
    }

    public static TelegramDigestGenerateResult failed(String channelName, String reason) {
        return new TelegramDigestGenerateResult(channelName, 0, false, reason);
    }
}
