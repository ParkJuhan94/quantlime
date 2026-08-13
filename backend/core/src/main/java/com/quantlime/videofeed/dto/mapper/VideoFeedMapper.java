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

    // Video는 유튜브 전용 엔티티로 남기고 텔레그램 글은 별도 TelegramPost로
    // 분리하기로 했으므로(Phase 8 P7 설계, docs/ROADMAP.md 참고) 이 TELEGRAM
    // 분기는 도달 불가능하다 - 삭제하지 않고 exhaustive switch로 남겨 향후
    // Video가 실수로 텔레그램에도 재사용되는 변경이 생기면 컴파일 에러로
    // 바로 드러나게 한다.
    private static String toVideoUrl(Video video) {
        return switch (video.getChannel().getPlatform()) {
            case YOUTUBE -> "https://www.youtube.com/watch?v=" + video.getExternalVideoId();
            case TELEGRAM -> null;
        };
    }

    private static String toChannelUrl(Channel channel) {
        return switch (channel.getPlatform()) {
            case YOUTUBE -> "https://www.youtube.com/channel/" + channel.getExternalChannelId();
            case TELEGRAM -> "https://t.me/" + channel.getExternalChannelId();
        };
    }
}
