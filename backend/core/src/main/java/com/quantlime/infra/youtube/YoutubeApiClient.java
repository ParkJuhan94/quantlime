package com.quantlime.infra.youtube;

import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.youtube.dto.YoutubePlaylistItemsResponse;
import com.quantlime.infra.youtube.dto.YoutubeVideosResponse;
import com.quantlime.infra.youtube.exception.YoutubeApiErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 유튜브 Data API v3 클라이언트. playlistItems.list(1u)/videos.list(1u)만
 * 사용한다 - search.list(100u)는 일 쿼터(10,000u)를 순식간에 소모해 절대
 * 쓰지 않는다(§7 리스크).
 */
@Component
@RequiredArgsConstructor
public class YoutubeApiClient {

    private static final int MAX_IDS_PER_REQUEST = 50;

    private final RestClient youtubeRestClient;
    private final YoutubeApiProperties properties;

    public YoutubePlaylistItemsResponse getPlaylistItems(String playlistId, String pageToken) {
        return ExternalApiInvoker.call(
            YoutubeApiErrorCode.PLAYLIST_ITEMS_INQUIRY_FAILED,
            () -> youtubeRestClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/playlistItems")
                        .queryParam("part", "snippet")
                        .queryParam("playlistId", playlistId)
                        .queryParam("maxResults", MAX_IDS_PER_REQUEST)
                        .queryParam("key", properties.getApiKey());
                    if (pageToken != null) {
                        builder.queryParam("pageToken", pageToken);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(YoutubePlaylistItemsResponse.class),
            HttpClientErrorException.Forbidden.class,
            YoutubeApiErrorCode.QUOTA_EXCEEDED);
    }

    /**
     * @param videoIds 최대 50개(그 이상은 호출측에서 분할해야 함)
     */
    public YoutubeVideosResponse getVideos(List<String> videoIds) {
        String ids = String.join(",", videoIds);
        return ExternalApiInvoker.call(
            YoutubeApiErrorCode.VIDEOS_INQUIRY_FAILED,
            () -> youtubeRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/videos")
                    .queryParam("part", "contentDetails,statistics")
                    .queryParam("id", ids)
                    .queryParam("key", properties.getApiKey())
                    .build())
                .retrieve()
                .body(YoutubeVideosResponse.class),
            HttpClientErrorException.Forbidden.class,
            YoutubeApiErrorCode.QUOTA_EXCEEDED);
    }
}
