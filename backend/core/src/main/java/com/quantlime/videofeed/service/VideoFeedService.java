package com.quantlime.videofeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Summary;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoTicker;
import com.quantlime.videofeed.dto.SummaryPayload;
import com.quantlime.videofeed.dto.mapper.VideoFeedMapper;
import com.quantlime.videofeed.dto.response.VideoFeedChannelResponse;
import com.quantlime.videofeed.dto.response.VideoFeedDetailResponse;
import com.quantlime.videofeed.dto.response.VideoFeedItemResponse;
import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.videofeed.implement.ChannelReader;
import com.quantlime.videofeed.implement.SummaryReader;
import com.quantlime.videofeed.implement.VideoReader;
import com.quantlime.videofeed.implement.VideoTickerReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoFeedService {

    private final ChannelReader channelReader;
    private final VideoReader videoReader;
    private final SummaryReader summaryReader;
    private final VideoTickerReader videoTickerReader;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Slice<VideoFeedItemResponse> getVideos(String tickerCode, Long channelId, LocalDate date, Pageable pageable) {
        LocalDateTime publishedFrom = date == null ? null : date.atStartOfDay();
        LocalDateTime publishedTo = date == null ? null : date.plusDays(1).atStartOfDay();
        Slice<Video> videos = videoReader.findSummarizedVideos(
            tickerCode, channelId, publishedFrom, publishedTo, pageable);

        List<Long> videoIds = videos.getContent().stream().map(Video::getId).toList();
        Map<Long, String> summaryByVideoId = toSummaryTextMap(summaryReader.findByVideo_IdIn(videoIds));
        Map<Long, List<VideoTicker>> tickersByVideoId = groupByVideoId(videoTickerReader.findByVideo_IdIn(videoIds));

        return videos.map(video -> VideoFeedMapper.toItemResponse(
            video,
            summaryByVideoId.getOrDefault(video.getId(), ""),
            tickersByVideoId.getOrDefault(video.getId(), List.of())));
    }

    // 채널 필터 UI(칩) 옵션 목록용 - 관리자용 채널 목록(ChannelQueryService,
    // filterConfig 등 운영 정보 포함)과 달리 활성 채널의 이름만 공개 노출한다.
    // Platform.YOUTUBE로 한정 - 이 API는 /api/video-feed(유튜브 전용) 채널
    // 필터라, 텔레그램 채널(Phase 8 P7)이 섞이면 선택해도 항상 빈 결과가 된다.
    @Transactional(readOnly = true)
    public List<VideoFeedChannelResponse> getChannels() {
        return channelReader.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.YOUTUBE).stream()
            .map(VideoFeedMapper::toVideoFeedChannelResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public VideoFeedDetailResponse getVideoDetail(Long videoId) {
        Video video = videoReader.findSummarizedVideoById(videoId)
            .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_VIDEO));
        Summary summary = summaryReader.findByVideo(video)
            .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_VIDEO));
        List<VideoTicker> tickers = videoTickerReader.findByVideo(video);
        return VideoFeedMapper.toDetailResponse(video, toPayload(summary), tickers);
    }

    private Map<Long, String> toSummaryTextMap(List<Summary> summaries) {
        Map<Long, String> result = new HashMap<>();
        for (Summary summary : summaries) {
            result.put(summary.getVideo().getId(), toPayload(summary).summary());
        }
        return result;
    }

    private Map<Long, List<VideoTicker>> groupByVideoId(List<VideoTicker> tickers) {
        return tickers.stream().collect(Collectors.groupingBy(ticker -> ticker.getVideo().getId()));
    }

    private SummaryPayload toPayload(Summary summary) {
        try {
            return objectMapper.readValue(summary.getPayload(), SummaryPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("요약 payload 역직렬화에 실패했습니다: summaryId=" + summary.getId(), e);
        }
    }
}
