package com.quantlime.videofeed.implement;

import com.quantlime.infra.youtube.YoutubeApiClient;
import com.quantlime.infra.youtube.dto.YoutubeChannelsResponse;
import com.quantlime.infra.youtube.dto.YoutubePlaylistItemsResponse;
import com.quantlime.infra.youtube.dto.YoutubeVideosResponse;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 유튜브 채널 메타데이터(프로필 사진, 최근 업로드 속도)를 가져와 도메인 값으로 변환하는 구현
 * 레이어(Implementation) - 외부 호출과 응답 해석을 이 컴포넌트가 맡고, 채널 초기화
 * 서비스는 어떤 채널을 언제 갱신하고 저장할지만 정한다. 영상 수집은
 * {@link YoutubeVideoCollector}가 맡는다.
 */
@Component
@RequiredArgsConstructor
public class YoutubeMetadataCollector {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final YoutubeApiClient youtubeApiClient;

    /** 채널ID → 기본 썸네일 URL. 썸네일이 없는 채널은 결과에서 빠진다. */
    public Map<String, String> fetchProfileImageUrls(List<String> channelIds) {
        YoutubeChannelsResponse response = youtubeApiClient.getChannels(channelIds);
        return response.items().stream()
            .filter(item -> item.snippet() != null && item.snippet().thumbnails() != null
                && item.snippet().thumbnails().defaultThumbnail() != null)
            .collect(Collectors.toMap(YoutubeChannelsResponse.Item::id,
                item -> item.snippet().thumbnails().defaultThumbnail().url()));
    }

    /** 업로드 플레이리스트 최신 {@code sampleSize}개 영상의 시간당 조회수(업로드 속도) 목록. */
    public List<BigDecimal> fetchRecentVelocities(String uploadsPlaylistId, int sampleSize) {
        // playlistItems.list maxResults=50(1u)이 최신순으로 오므로 상위
        // sampleSize개만 잘라 쓰면 페이지네이션 없이 1회 호출로 충분하다.
        YoutubePlaylistItemsResponse playlistResponse = youtubeApiClient.getPlaylistItems(uploadsPlaylistId, null);
        List<YoutubePlaylistItemsResponse.Item> items = playlistResponse.items().stream()
            .limit(sampleSize)
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
        // publishedAt이 SEOUL로 zone-strip된 값이라 bare LocalDateTime.now()(JVM
        // 기본 타임존)와 비교하면 안 된다 - 프로덕션은 Dockerfile의 TZ=Asia/Seoul
        // 고정 덕에 우연히 맞았지만, CI(ubuntu-latest 기본 UTC)에서 9시간이 밀려
        // hoursSincePublish가 10→1로 잘못 계산되는 걸 실제로 재현해 확인함(2026-09-10).
        long hoursSincePublish = Math.max(Duration.between(publishedAt, LocalDateTime.now(SEOUL)).toHours(), 1);
        return BigDecimal.valueOf(viewCount).divide(BigDecimal.valueOf(hoursSincePublish), 4, RoundingMode.HALF_UP);
    }
}
