package com.quantlime.videofeed.dto.mapper;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoTicker;
import com.quantlime.videofeed.dto.SummaryPayload;
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
            payload.caveat(),
            tickers.stream().map(VideoFeedMapper::toTickerResponse).toList());
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
