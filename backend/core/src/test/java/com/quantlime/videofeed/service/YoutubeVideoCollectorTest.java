package com.quantlime.videofeed.service;

import com.quantlime.infra.youtube.YoutubeApiClient;
import com.quantlime.infra.youtube.dto.YoutubePlaylistItemsResponse;
import com.quantlime.infra.youtube.dto.YoutubeVideosResponse;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.dto.CollectedVideo;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

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
}
