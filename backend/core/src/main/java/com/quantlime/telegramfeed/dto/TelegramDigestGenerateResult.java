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

    // failed() 팩토리는 제거됐다(2026-09-30, 카프카 다도메인 확장 Phase 4) -
    // 생성 실패를 이 DTO로 감싸 흡수하던 이전 동기 루프가 없어지고, 이제
    // TelegramDigestGenerationFacade.generateForChannel이 실패를 그대로
    // 던져 Kafka 재시도/DLT가 처리한다(success 필드가 false인 인스턴스는
    // 더 이상 만들어지지 않는다).
}
