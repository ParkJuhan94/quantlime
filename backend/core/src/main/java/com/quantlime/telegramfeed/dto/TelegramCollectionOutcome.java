package com.quantlime.telegramfeed.dto;

import java.util.List;

// TelegramPostCollector.collect()의 결과 - 채널 메타(og:title/og:image)를
// 별도 HTTP 요청으로 다시 조회하지 않고, 수집 중 이미 받은 페이지 응답에서
// 그대로 얻는다(중복 요청을 피하는 정중한 스크래핑 정책의 일부).
public record TelegramCollectionOutcome(List<CollectedTelegramPost> posts, TelegramChannelMeta channelMeta) {
}
