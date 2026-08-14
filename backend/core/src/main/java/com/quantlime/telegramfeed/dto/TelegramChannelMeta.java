package com.quantlime.telegramfeed.dto;

// 채널 시딩/프로필 사진 갱신용 - TelegramPostCollector.fetchChannelMeta()의 반환값.
public record TelegramChannelMeta(String title, String photoUrl) {
}
