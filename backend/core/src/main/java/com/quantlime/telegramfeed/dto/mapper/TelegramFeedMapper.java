package com.quantlime.telegramfeed.dto.mapper;

import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.telegramfeed.domain.TelegramDigestTicker;
import com.quantlime.telegramfeed.dto.TelegramSummaryPayload;
import com.quantlime.telegramfeed.dto.response.TelegramChannelResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedChannelResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDigestDetailResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDigestResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedTickerResponse;
import com.quantlime.videofeed.domain.Channel;
import java.util.List;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class TelegramFeedMapper {

    public static TelegramChannelResponse toChannelResponse(Channel channel) {
        return new TelegramChannelResponse(
            channel.getId(),
            channel.getExternalChannelId(),
            toChannelUrl(channel),
            channel.getName(),
            channel.isEnabled(),
            channel.getPriority(),
            channel.getTelegramFilterConfig(),
            channel.getLastCollectedAt(),
            channel.getProfileImageUrl());
    }

    public static TelegramFeedDigestResponse toDigestResponse(
        TelegramDigest digest, String summary, List<TelegramDigestTicker> tickers, int sourcePostCount) {
        Channel channel = digest.getChannel();
        return new TelegramFeedDigestResponse(
            digest.getId(),
            channel.getName(),
            channel.getProfileImageUrl(),
            toChannelUrl(channel),
            digest.getDigestDate(),
            sourcePostCount,
            summary,
            tickers.stream().map(TelegramFeedMapper::toTickerResponse).toList());
    }

    public static TelegramFeedDigestDetailResponse toDigestDetailResponse(
        TelegramDigest digest, TelegramSummaryPayload payload, List<TelegramDigestTicker> tickers,
        List<String> sourcePostUrls) {
        Channel channel = digest.getChannel();
        return new TelegramFeedDigestDetailResponse(
            digest.getId(),
            channel.getName(),
            channel.getProfileImageUrl(),
            toChannelUrl(channel),
            digest.getDigestDate(),
            sourcePostUrls,
            payload.summary(),
            payload.keyPoints(),
            // macro_points는 유튜브 쪽과 동일하게 과거 요약 데이터에 키 자체가
            // 없을 수 있어 방어(VideoFeedMapper.toDetailResponse와 동일 이유).
            payload.macroPoints() != null ? payload.macroPoints() : List.of(),
            payload.caveat(),
            tickers.stream().map(TelegramFeedMapper::toTickerResponse).toList());
    }

    public static TelegramFeedChannelResponse toFeedChannelResponse(Channel channel) {
        return new TelegramFeedChannelResponse(channel.getId(), channel.getName());
    }

    private static TelegramFeedTickerResponse toTickerResponse(TelegramDigestTicker ticker) {
        return new TelegramFeedTickerResponse(
            ticker.getTickerCode(), ticker.getTickerName(), ticker.getStance(), ticker.getConfidence());
    }

    private static String toChannelUrl(Channel channel) {
        return "https://t.me/" + channel.getExternalChannelId();
    }
}
