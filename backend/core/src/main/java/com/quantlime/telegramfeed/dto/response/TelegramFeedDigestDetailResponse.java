package com.quantlime.telegramfeed.dto.response;

import java.time.LocalDate;
import java.util.List;

public record TelegramFeedDigestDetailResponse(
    Long telegramDigestId,
    String channelName,
    String channelProfileImageUrl,
    String channelUrl,
    LocalDate digestDate,
    // 다이제스트가 여러 글을 합친 결과라 원문이 하나가 아니다 - 그날 재료가
    // 된 글의 원문 링크 목록을 그대로 나열한다(발행시각순).
    List<String> sourcePostUrls,
    String summary,
    List<String> keyPoints,
    List<String> macroPoints,
    String caveat,
    List<TelegramFeedTickerResponse> tickers
) {
}
