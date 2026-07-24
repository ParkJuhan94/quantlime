package com.quantlime.videofeed.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.infra.youtube.YoutubeApiClient;
import com.quantlime.infra.youtube.dto.YoutubePlaylistItemsResponse;
import com.quantlime.infra.youtube.dto.YoutubeVideosResponse;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채널별 최근 30개 업로드의 views/hours 중앙값을 산정한다(§8 첫 작업
 * 순서 7번). velocity_multiplier가 0인 채널(개인 채널)은 filter_config
 * 자체가 velocity 검사를 건너뛰므로 이 값이 없어도 무방하지만, 나중에
 * multiplier를 바꿀 수도 있어 모든 채널에 대해 계산해둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelVelocityInitializationService {

    private static final int SAMPLE_SIZE = 30;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final YoutubeApiClient youtubeApiClient;
    private final ChannelRepository channelRepository;

    public BigDecimal initializeMedianVelocity(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_CHANNEL));

        List<BigDecimal> velocities = fetchRecentVelocities(channel.getUploadsPlaylistId());
        BigDecimal median = median(velocities);
        persistMedianVelocity(channel.getId(), median);
        log.info("중앙값 업로드 속도 산정 완료: channel={}, median={}, sampleSize={}",
            channel.getName(), median, velocities.size());
        return median;
    }

    private List<BigDecimal> fetchRecentVelocities(String uploadsPlaylistId) {
        // playlistItems.list maxResults=50(1u)이 최신순으로 오므로 상위
        // 30개만 잘라 쓰면 페이지네이션 없이 1회 호출로 충분하다.
        YoutubePlaylistItemsResponse playlistResponse = youtubeApiClient.getPlaylistItems(uploadsPlaylistId, null);
        List<YoutubePlaylistItemsResponse.Item> items = playlistResponse.items().stream()
            .limit(SAMPLE_SIZE)
            .toList();
        if (items.isEmpty()) {
            return List.of();
        }

        List<String> videoIds = items.stream()
            .map(item -> item.snippet().resourceId().videoId())
            .toList();
        YoutubeVideosResponse videosResponse = youtubeApiClient.getVideos(videoIds);
        Map<String, YoutubeVideosResponse.Item> detailsById = new HashMap<>();
        for (YoutubeVideosResponse.Item item : videosResponse.items()) {
            detailsById.put(item.id(), item);
        }

        return items.stream()
            .map(item -> toVelocity(item, detailsById.get(item.snippet().resourceId().videoId())))
            .filter(Objects::nonNull)
            .toList();
    }

    private BigDecimal toVelocity(YoutubePlaylistItemsResponse.Item item, YoutubeVideosResponse.Item details) {
        if (details == null || details.statistics() == null || details.statistics().viewCount() == null) {
            return null;
        }
        long viewCount = Long.parseLong(details.statistics().viewCount());
        LocalDateTime publishedAt = Instant.parse(item.snippet().publishedAt()).atZone(SEOUL).toLocalDateTime();
        long hoursSincePublish = Math.max(Duration.between(publishedAt, LocalDateTime.now()).toHours(), 1);
        return BigDecimal.valueOf(viewCount).divide(BigDecimal.valueOf(hoursSincePublish), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int size = sorted.size();
        int mid = size / 2;
        if (size % 2 == 1) {
            return sorted.get(mid);
        }
        return sorted.get(mid - 1).add(sorted.get(mid))
            .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    @Transactional
    void persistMedianVelocity(Long channelId, BigDecimal median) {
        Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_CHANNEL));
        channel.updateMedianVelocity(median);
    }
}
