package com.quantlime.telegramfeed.dto.response;

import java.time.LocalDateTime;
import java.util.List;

// 목록 API 전용 - 원문 전문/keyPoints/macroPoints/caveat는 상세(TelegramFeedDetailResponse)
// 에만 담는다(VideoFeedItemResponse와 동일 이유: Summary.payload 전체를 페이지당 N개씩
// 매번 실어보내지 않기 위함). 유튜브의 title/videoUrl/durationSec 자리에 postUrl만 있고
// 제목·재생시간 개념 자체가 없다(텔레그램 글은 제목 없는 텍스트 게시물).
public record TelegramFeedPostResponse(
    Long telegramPostId,
    String channelName,
    String channelProfileImageUrl,
    String channelUrl,
    String postUrl,
    LocalDateTime publishedAt,
    Long viewCount,
    String summary,
    List<TelegramFeedTickerResponse> tickers
) {
}
