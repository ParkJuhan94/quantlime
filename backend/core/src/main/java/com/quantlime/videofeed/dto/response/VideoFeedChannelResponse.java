package com.quantlime.videofeed.dto.response;

// 영상 요약 피드 채널 필터용 - 관리자용 ChannelResponse(내부 운영 정보인
// filterConfig/medianVelocity/lastCollectedAt 포함)와 달리 공개 API라
// 필터 UI에 필요한 최소 정보만 노출한다.
public record VideoFeedChannelResponse(
    Long channelId,
    String name
) {
}
