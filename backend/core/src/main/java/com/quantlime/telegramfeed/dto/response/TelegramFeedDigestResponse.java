package com.quantlime.telegramfeed.dto.response;

import java.time.LocalDate;
import java.util.List;

// 목록 API 전용(2026-08-15 다이제스트 재설계) - keyPoints/macroPoints/caveat/
// sourcePostUrls는 상세(TelegramFeedDigestDetailResponse)에만 담는다
// (VideoFeedItemResponse와 동일 이유). sourcePostCount는 "N개 게시물 종합"
// 같은 UI 표기용.
public record TelegramFeedDigestResponse(
    Long telegramDigestId,
    String channelName,
    String channelProfileImageUrl,
    String channelUrl,
    LocalDate digestDate,
    int sourcePostCount,
    String summary,
    List<TelegramFeedTickerResponse> tickers
) {
}
