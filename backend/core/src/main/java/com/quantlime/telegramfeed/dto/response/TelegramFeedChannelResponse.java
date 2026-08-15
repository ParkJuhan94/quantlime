package com.quantlime.telegramfeed.dto.response;

// videofeed.dto.response.VideoFeedChannelResponse와 동일 - 공개 채널 필터 UI용
// 최소 정보(관리자용 TelegramChannelResponse의 filterConfig/lastCollectedAt 등
// 운영 정보는 노출하지 않는다).
public record TelegramFeedChannelResponse(
    Long channelId,
    String name
) {
}
