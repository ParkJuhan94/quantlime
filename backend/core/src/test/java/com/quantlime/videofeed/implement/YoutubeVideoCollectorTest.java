package com.quantlime.videofeed.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.youtube.YoutubeApiClient;
import com.quantlime.infra.youtube.dto.YoutubePlaylistItemsResponse;
import com.quantlime.infra.youtube.dto.YoutubeVideosResponse;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.dto.CollectedVideo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class YoutubeVideoCollectorTest {

    @Mock
    private YoutubeApiClient youtubeApiClient;

    @InjectMocks
    private YoutubeVideoCollector youtubeVideoCollector;

    @Test
    @DisplayName("[last_collected_at보다 오래된 영상을 만나면 그 이전 페이지에서 수집을 멈춘다]")
    void collect_stopsAtIncrementalCutoff() {
        // given
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 0.0, 5, List.of(), List.of()));
        channel.updateLastCollectedAt(LocalDateTime.parse("2026-07-20T00:00:00"));

        YoutubePlaylistItemsResponse response = new YoutubePlaylistItemsResponse(null, List.of(
            new YoutubePlaylistItemsResponse.Item(new YoutubePlaylistItemsResponse.Snippet(
                "신규 영상", "2026-07-22T00:00:00Z",
                new YoutubePlaylistItemsResponse.ResourceId("video-new"))),
            new YoutubePlaylistItemsResponse.Item(new YoutubePlaylistItemsResponse.Snippet(
                "이미 수집된 영상", "2026-07-19T00:00:00Z",
                new YoutubePlaylistItemsResponse.ResourceId("video-old")))
        ));
        given(youtubeApiClient.getPlaylistItems("UUtest", null)).willReturn(response);
        given(youtubeApiClient.getVideos(anyList())).willReturn(new YoutubeVideosResponse(List.of(
            new YoutubeVideosResponse.Item("video-new",
                new YoutubeVideosResponse.ContentDetails("PT15M33S"),
                new YoutubeVideosResponse.Statistics("1234"))
        )));

        // when
        List<CollectedVideo> result = youtubeVideoCollector.collect(channel);

        // then
        assertThat(result).hasSize(1);
        CollectedVideo collected = result.get(0);
        assertThat(collected.externalVideoId()).isEqualTo("video-new");
        assertThat(collected.durationSec()).isEqualTo(933);
        assertThat(collected.viewCount()).isEqualTo(1234L);
    }

    private Channel channelSince(String lastCollectedAt) {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 0.0, 5, List.of(), List.of()));
        if (lastCollectedAt != null) {
            channel.updateLastCollectedAt(LocalDateTime.parse(lastCollectedAt));
        }
        return channel;
    }

    private YoutubePlaylistItemsResponse page(String nextPageToken, int count, String idPrefix) {
        List<YoutubePlaylistItemsResponse.Item> items = IntStream.range(0, count)
            .mapToObj(i -> new YoutubePlaylistItemsResponse.Item(new YoutubePlaylistItemsResponse.Snippet(
                "영상 " + i, "2026-07-22T00:00:00Z", new YoutubePlaylistItemsResponse.ResourceId(idPrefix + i))))
            .toList();
        return new YoutubePlaylistItemsResponse(nextPageToken, items);
    }

    private YoutubeVideosResponse detailsFor(List<String> ids) {
        return new YoutubeVideosResponse(ids.stream()
            .map(id -> new YoutubeVideosResponse.Item(id,
                new YoutubeVideosResponse.ContentDetails("PT1M"), new YoutubeVideosResponse.Statistics("10")))
            .toList());
    }

    @Test
    @DisplayName("[새 영상이 없으면 상세 조회 없이 빈 목록을 돌려준다]")
    void collect_noNewItems_skipsDetailLookup() {
        given(youtubeApiClient.getPlaylistItems("UUtest", null)).willReturn(page(null, 0, "v"));

        assertThat(youtubeVideoCollector.collect(channelSince(null))).isEmpty();
        verify(youtubeApiClient, never()).getVideos(anyList());
    }

    @Test
    @DisplayName("[최초 수집(마지막 수집 시각 없음)은 다음 페이지가 계속 있어도 4페이지에서 멈춘다]")
    void collect_firstRun_capsAtFourPages() {
        given(youtubeApiClient.getPlaylistItems(eq("UUtest"), any())).willReturn(page("next", 1, "v"));
        given(youtubeApiClient.getVideos(anyList())).willReturn(new YoutubeVideosResponse(List.of()));

        youtubeVideoCollector.collect(channelSince(null));

        verify(youtubeApiClient, times(4)).getPlaylistItems(eq("UUtest"), any());
    }

    @Test
    @DisplayName("[증분 수집은 컷오프를 만나기 전까지 nextPageToken을 따라 다음 페이지로 간다]")
    void collect_incremental_followsNextPageToken() {
        given(youtubeApiClient.getPlaylistItems("UUtest", null)).willReturn(page("p2", 1, "a"));
        given(youtubeApiClient.getPlaylistItems("UUtest", "p2")).willReturn(page(null, 1, "b"));
        given(youtubeApiClient.getVideos(anyList())).willReturn(detailsFor(List.of("a0", "b0")));

        List<CollectedVideo> result = youtubeVideoCollector.collect(channelSince("2026-07-01T00:00:00"));

        assertThat(result).extracting(CollectedVideo::externalVideoId).containsExactly("a0", "b0");
    }

    @Test
    @DisplayName("[상세 정보가 없는 영상도 길이·조회수를 null로 두고 목록에는 남긴다]")
    void collect_missingDetails_keepsVideoWithNulls() {
        given(youtubeApiClient.getPlaylistItems("UUtest", null)).willReturn(page(null, 1, "v"));
        given(youtubeApiClient.getVideos(anyList())).willReturn(new YoutubeVideosResponse(List.of()));

        List<CollectedVideo> result = youtubeVideoCollector.collect(channelSince(null));

        assertThat(result).singleElement().satisfies(v -> {
            assertThat(v.durationSec()).isNull();
            assertThat(v.viewCount()).isNull();
        });
    }

    @Test
    @DisplayName("[상세 조회는 50개씩 나눠 보낸다]")
    void collect_detailLookupIsBatchedBy50() {
        given(youtubeApiClient.getPlaylistItems("UUtest", null)).willReturn(page(null, 120, "v"));
        given(youtubeApiClient.getVideos(anyList())).willReturn(new YoutubeVideosResponse(List.of()));

        youtubeVideoCollector.collect(channelSince(null));

        ArgumentCaptor<List<String>> batches = ArgumentCaptor.forClass(List.class);
        verify(youtubeApiClient, times(3)).getVideos(batches.capture());
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(50, 50, 20);
    }

    @Test
    @DisplayName("[조회수 재조회 - 통계가 없는 영상은 결과에서 빠지고 50개씩 나눠 조회한다]")
    void fetchViewCounts_skipsItemsWithoutStatistics() {
        List<String> ids = IntStream.range(0, 60).mapToObj(i -> "v" + i).toList();
        given(youtubeApiClient.getVideos(ids.subList(0, 50))).willReturn(new YoutubeVideosResponse(List.of(
            new YoutubeVideosResponse.Item("v0", null, new YoutubeVideosResponse.Statistics("500")),
            new YoutubeVideosResponse.Item("v1", null, null),
            new YoutubeVideosResponse.Item("v2", null, new YoutubeVideosResponse.Statistics(null)))));
        given(youtubeApiClient.getVideos(ids.subList(50, 60))).willReturn(new YoutubeVideosResponse(List.of(
            new YoutubeVideosResponse.Item("v55", null, new YoutubeVideosResponse.Statistics("7")))));

        Map<String, Long> counts = youtubeVideoCollector.fetchViewCounts(ids);

        assertThat(counts).containsOnly(Map.entry("v0", 500L), Map.entry("v55", 7L));
    }
}
