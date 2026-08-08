package com.quantlime.videofeed.dto.mapper;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoTicker;
import com.quantlime.videofeed.dto.SummaryPayload;
import com.quantlime.videofeed.dto.response.ChannelResponse;
import com.quantlime.videofeed.dto.response.VideoFeedChannelResponse;
import com.quantlime.videofeed.dto.response.VideoFeedDetailResponse;
import com.quantlime.videofeed.dto.response.VideoFeedItemResponse;
import com.quantlime.videofeed.dto.response.VideoFeedTickerResponse;
import java.util.List;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class VideoFeedMapper {

    public static VideoFeedItemResponse toItemResponse(
        Video video, String summary, List<VideoTicker> tickers) {
        Channel channel = video.getChannel();
        return new VideoFeedItemResponse(
            video.getId(),
            channel.getName(),
            channel.getProfileImageUrl(),
            toChannelUrl(channel),
            video.getTitle(),
            toVideoUrl(video),
            video.getPublishedAt(),
            video.getDurationSec(),
            summary,
            tickers.stream().map(VideoFeedMapper::toTickerResponse).toList());
    }

    public static VideoFeedDetailResponse toDetailResponse(
        Video video, SummaryPayload payload, List<VideoTicker> tickers) {
        Channel channel = video.getChannel();
        return new VideoFeedDetailResponse(
            video.getId(),
            channel.getName(),
            channel.getProfileImageUrl(),
            toChannelUrl(channel),
            video.getTitle(),
            toVideoUrl(video),
            video.getPublishedAt(),
            video.getDurationSec(),
            payload.summary(),
            payload.keyPoints(),
            // macro_points는 2026-08-08 신규 필드라, 그 이전에 생성된 Summary
            // payload에는 키 자체가 없어 역직렬화 시 null이 된다 - 프론트가 항상
            // 리스트로 다룰 수 있도록 빈 리스트로 방어한다(재요약 전까지는 과거
            // 영상에 매크로 코멘트가 비어 보이는 게 정상 - 지어내지 않음).
            payload.macroPoints() != null ? payload.macroPoints() : List.of(),
            payload.caveat(),
            tickers.stream().map(VideoFeedMapper::toTickerResponse).toList());
    }

    public static VideoFeedChannelResponse toVideoFeedChannelResponse(Channel channel) {
        return new VideoFeedChannelResponse(channel.getId(), channel.getName());
    }

    public static ChannelResponse toChannelResponse(Channel channel) {
        return new ChannelResponse(
            channel.getId(),
            channel.getPlatform(),
            channel.getExternalChannelId(),
            toChannelUrl(channel),
            channel.getName(),
            channel.isEnabled(),
            channel.getPriority(),
            channel.getFilterConfig(),
            channel.getMedianVelocity(),
            channel.getLastCollectedAt(),
            channel.getProfileImageUrl());
    }

    private static VideoFeedTickerResponse toTickerResponse(VideoTicker ticker) {
        return new VideoFeedTickerResponse(
            ticker.getTickerCode(), ticker.getTickerName(), ticker.getStance(), ticker.getConfidence());
    }

    // 현재 시딩된 채널은 전부 YOUTUBE라 이 케이스만 실제로 쓰이지만, 스키마가
    // 이미 Platform.TELEGRAM을 지원하므로(P7 준비) exhaustive switch로 강제해
    // 나중에 텔레그램 채널이 추가되는 순간 컴파일 에러로 이 분기 추가를 상기시킨다.
    private static String toVideoUrl(Video video) {
        return switch (video.getChannel().getPlatform()) {
            case YOUTUBE -> "https://www.youtube.com/watch?v=" + video.getExternalVideoId();
            case TELEGRAM -> null;
        };
    }

    private static String toChannelUrl(Channel channel) {
        return switch (channel.getPlatform()) {
            case YOUTUBE -> "https://www.youtube.com/channel/" + channel.getExternalChannelId();
            case TELEGRAM -> null;
        };
    }
}
