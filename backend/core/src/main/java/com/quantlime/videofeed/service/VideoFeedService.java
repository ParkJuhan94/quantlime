package com.quantlime.videofeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.videofeed.domain.Summary;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoTicker;
import com.quantlime.videofeed.dto.SummaryPayload;
import com.quantlime.videofeed.dto.mapper.VideoFeedMapper;
import com.quantlime.videofeed.dto.response.VideoFeedDetailResponse;
import com.quantlime.videofeed.dto.response.VideoFeedItemResponse;
import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.videofeed.repository.SummaryRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import com.quantlime.videofeed.repository.VideoTickerRepository;
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

    private final VideoRepository videoRepository;
    private final SummaryRepository summaryRepository;
    private final VideoTickerRepository videoTickerRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Slice<VideoFeedItemResponse> getVideos(String tickerCode, LocalDate date, Pageable pageable) {
        LocalDateTime publishedFrom = date == null ? null : date.atStartOfDay();
        LocalDateTime publishedTo = date == null ? null : date.plusDays(1).atStartOfDay();
        Slice<Video> videos = videoRepository.findSummarizedVideos(tickerCode, publishedFrom, publishedTo, pageable);

        List<Long> videoIds = videos.getContent().stream().map(Video::getId).toList();
        Map<Long, String> summaryByVideoId = toSummaryTextMap(summaryRepository.findByVideo_IdIn(videoIds));
        Map<Long, List<VideoTicker>> tickersByVideoId = groupByVideoId(videoTickerRepository.findByVideo_IdIn(videoIds));

        return videos.map(video -> VideoFeedMapper.toItemResponse(
            video,
            summaryByVideoId.getOrDefault(video.getId(), ""),
            tickersByVideoId.getOrDefault(video.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public VideoFeedDetailResponse getVideoDetail(Long videoId) {
        Video video = videoRepository.findSummarizedVideoById(videoId)
            .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_VIDEO));
        Summary summary = summaryRepository.findByVideo(video)
            .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_VIDEO));
        List<VideoTicker> tickers = videoTickerRepository.findByVideo(video);
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
