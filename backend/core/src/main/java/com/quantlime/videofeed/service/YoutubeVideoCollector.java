package com.quantlime.videofeed.service;

import com.quantlime.infra.youtube.YoutubeApiClient;
import com.quantlime.infra.youtube.dto.YoutubePlaylistItemsResponse;
import com.quantlime.infra.youtube.dto.YoutubeVideosResponse;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.dto.CollectedVideo;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 유튜브 API 호출(외부 I/O)만 담당한다 - DB 트랜잭션과 절대 섞지 않는다
 * (§5 트랜잭션 경계 규칙, LLM 호출은 이후 단계지만 이 원칙을 수집
 * 단계부터 지킨다).
 */
@Component
@RequiredArgsConstructor
public class YoutubeVideoCollector {

    private static final int BATCH_SIZE = 50;
    // 최초 수집(lastCollectedAt 없음)은 채널 전체 업로드 이력을 훑지 않고
    // 최근 N페이지(최대 200개)로 제한한다 - 한국경제TV처럼 수천 개 영상을
    // 가진 채널에서 API 쿼터를 첫 실행에 전부 소진하는 것을 막기 위함.
    private static final int MAX_PAGES_ON_FIRST_RUN = 4;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final YoutubeApiClient youtubeApiClient;

    public List<CollectedVideo> collect(Channel channel) {
        LocalDateTime since = channel.getLastCollectedAt();
        List<PlaylistItemRef> refs = fetchNewPlaylistItems(channel.getUploadsPlaylistId(), since);
        if (refs.isEmpty()) {
            return List.of();
        }
        Map<String, YoutubeVideosResponse.Item> detailsById = fetchVideoDetails(refs);
        return refs.stream()
            .map(ref -> toCollectedVideo(ref, detailsById.get(ref.videoId())))
            .toList();
    }

    /**
     * PENDING_REVIEW 재평가 직전에 최신 조회수를 다시 받아오는 용도(2026-08-02
     * 신규) - DB에 저장된 view_count는 최초 발견 시점(대부분 발행 직후,
     * 조회수가 거의 0일 때) 스냅샷이라 그대로 재평가에 쓰면 6시간 유예를
     * 두는 의미가 없어지는 버그가 있었다. API가 값을 못 준 videoId(삭제/
     * 비공개 등)는 결과 맵에서 그냥 빠진다 - 호출측이 기존 view_count로
     * 폴백한다.
     */
    public Map<String, Long> fetchViewCounts(List<String> externalVideoIds) {
        Map<String, Long> viewCountsById = new HashMap<>();
        for (int i = 0; i < externalVideoIds.size(); i += BATCH_SIZE) {
            List<String> batch = externalVideoIds.subList(i, Math.min(i + BATCH_SIZE, externalVideoIds.size()));
            YoutubeVideosResponse response = youtubeApiClient.getVideos(batch);
            for (YoutubeVideosResponse.Item item : response.items()) {
                if (item.statistics() != null && item.statistics().viewCount() != null) {
                    viewCountsById.put(item.id(), Long.parseLong(item.statistics().viewCount()));
                }
            }
        }
        return viewCountsById;
    }

    private List<PlaylistItemRef> fetchNewPlaylistItems(String uploadsPlaylistId, LocalDateTime since) {
        List<PlaylistItemRef> refs = new ArrayList<>();
        String pageToken = null;
        int pagesFetched = 0;
        do {
            YoutubePlaylistItemsResponse response = youtubeApiClient.getPlaylistItems(uploadsPlaylistId, pageToken);
            pagesFetched++;
            boolean reachedCutoff = false;
            for (YoutubePlaylistItemsResponse.Item item : response.items()) {
                LocalDateTime publishedAt = parsePublishedAt(item.snippet().publishedAt());
                if (since != null && !publishedAt.isAfter(since)) {
                    reachedCutoff = true;
                    break;
                }
                refs.add(new PlaylistItemRef(
                    item.snippet().resourceId().videoId(), item.snippet().title(), publishedAt));
            }
            if (reachedCutoff) {
                break;
            }
            pageToken = response.nextPageToken();
            if (since == null && pagesFetched >= MAX_PAGES_ON_FIRST_RUN) {
                break;
            }
        } while (pageToken != null);
        return refs;
    }

    private Map<String, YoutubeVideosResponse.Item> fetchVideoDetails(List<PlaylistItemRef> refs) {
        Map<String, YoutubeVideosResponse.Item> detailsById = new HashMap<>();
        for (int i = 0; i < refs.size(); i += BATCH_SIZE) {
            List<String> batchIds = refs.subList(i, Math.min(i + BATCH_SIZE, refs.size())).stream()
                .map(PlaylistItemRef::videoId)
                .toList();
            YoutubeVideosResponse response = youtubeApiClient.getVideos(batchIds);
            for (YoutubeVideosResponse.Item item : response.items()) {
                detailsById.put(item.id(), item);
            }
        }
        return detailsById;
    }

    private CollectedVideo toCollectedVideo(PlaylistItemRef ref, YoutubeVideosResponse.Item details) {
        // 라이브 방송 중이거나 예정된 프리미어처럼 아직 러닝타임이 확정되지
        // 않은 영상은 contentDetails.duration 자체가 비어 온다(Duration.parse가
        // null을 그대로 던지면 스트림 처리 중 예외가 터져 그 배치 전체가
        // 실패한다 - 2026-09-28 실제 발생, 한국경제TV 채널 수집 전체가
        // 이 한 영상 때문에 통째로 실패했음).
        String duration = details != null && details.contentDetails() != null
            ? details.contentDetails().duration()
            : null;
        Integer durationSec = duration != null && !duration.isBlank()
            ? (int) Duration.parse(duration).toSeconds()
            : null;
        Long viewCount = details != null && details.statistics() != null && details.statistics().viewCount() != null
            ? Long.parseLong(details.statistics().viewCount())
            : null;
        return new CollectedVideo(ref.videoId(), ref.title(), ref.publishedAt(), durationSec, viewCount);
    }

    private LocalDateTime parsePublishedAt(String iso) {
        return Instant.parse(iso).atZone(SEOUL).toLocalDateTime();
    }

    private record PlaylistItemRef(String videoId, String title, LocalDateTime publishedAt) {
    }
}
