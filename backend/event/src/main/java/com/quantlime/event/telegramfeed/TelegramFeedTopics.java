package com.quantlime.event.telegramfeed;

/** 텔레그램 다이제스트 생성 이벤트화(2026-09-30, 카프카 다도메인 확장 Phase 4)가 쓰는 토픽. */
public final class TelegramFeedTopics {

    public static final String TELEGRAM_DIGEST_GENERATION_REQUESTED = "telegram.digest.generation.requested";

    private TelegramFeedTopics() {
    }
}
