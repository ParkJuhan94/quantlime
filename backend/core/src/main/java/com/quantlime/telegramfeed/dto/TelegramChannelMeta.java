package com.quantlime.telegramfeed.dto;

import org.springframework.lang.Nullable;

// 채널 시딩/프로필 사진 갱신용 - TelegramPostCollector.fetchChannelMeta()의 반환값.
public record TelegramChannelMeta(@Nullable String title, @Nullable String photoUrl) {
}
