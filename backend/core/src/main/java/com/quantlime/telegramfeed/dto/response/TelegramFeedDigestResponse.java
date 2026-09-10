package com.quantlime.telegramfeed.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 목록 API 전용(2026-08-15 다이제스트 재설계) - keyPoints/macroPoints/caveat/
// sourcePostUrls는 상세(TelegramFeedDigestDetailResponse)에만 담는다
// (VideoFeedItemResponse와 동일 이유). sourcePostCount는 "N개 게시물 종합"
// 같은 UI 표기용. updatedAt은 다이제스트가 하루 3회 upsert로 계속 덮어써지는
// UX 문제(리뷰 세션 Q2)에 대한 최소 대응 - 버전 이력 없이 "몇 시 기준
// 최신본인지"만 노출한다(TelegramDigest가 상속하는 TimeBaseEntity의
// @LastModifiedDate, overwrite() 호출마다 자동 갱신됨, 2026-09-10).
public record TelegramFeedDigestResponse(
    Long telegramDigestId,
    String channelName,
    String channelProfileImageUrl,
    String channelUrl,
    LocalDate digestDate,
    LocalDateTime updatedAt,
    int sourcePostCount,
    String summary,
    List<TelegramFeedTickerResponse> tickers
) {
}
