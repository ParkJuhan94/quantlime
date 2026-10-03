package com.quantlime.videofeed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.youtube.YoutubeApiClient;
import com.quantlime.infra.youtube.dto.YoutubeChannelsResponse;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.implement.ChannelAppender;
import com.quantlime.videofeed.implement.ChannelReader;
import com.quantlime.videofeed.implement.YoutubeMetadataCollector;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ChannelSeedInitializerTest {

    @Mock
    private ChannelReader channelReader;

    @Mock
    private ChannelAppender channelAppender;

    @Mock
    private YoutubeApiClient youtubeApiClient;

    private ChannelSeedInitializer initializer;

    // 대상 선정·저장(service)과 외부 호출(implement)을 함께 검증한다 - 외부 클라이언트만 mock.
    @BeforeEach
    void setUpService() {
        initializer = new ChannelSeedInitializer(
            channelReader, channelAppender, new YoutubeMetadataCollector(youtubeApiClient));
    }

    private Channel youtubeChannel(String externalId, String name) {
        return Channel.of(Platform.YOUTUBE, externalId, "UU" + externalId.substring(2), name, 20,
            new ChannelFilterConfig(180, 0.0, 3, List.of(), List.of()));
    }

    private YoutubeChannelsResponse thumbnails(String id, String url) {
        return new YoutubeChannelsResponse(List.of(new YoutubeChannelsResponse.Item(id,
            new YoutubeChannelsResponse.Snippet(
                new YoutubeChannelsResponse.Thumbnails(new YoutubeChannelsResponse.Thumbnail(url))))));
    }

    @Test
    @DisplayName("[이미 있는 채널은 건너뛰고, 없는 채널은 업로드 플레이리스트 ID(UU+채널ID 뒷부분)와 함께 저장한다]")
    void seedIfAbsent_skipsExisting_savesNewWithUploadsPlaylist() {
        // given
        given(channelReader.existsByPlatformAndExternalChannelId(Platform.YOUTUBE, "UCexisting")).willReturn(true);
        given(channelReader.existsByPlatformAndExternalChannelId(Platform.YOUTUBE, "UCnewchannel")).willReturn(false);
        ChannelFilterConfig filter = new ChannelFilterConfig(180, 0.0, 3, List.of(), List.of());

        // when
        initializer.seedIfAbsent("UCexisting", "기존", 10, filter);
        initializer.seedIfAbsent("UCnewchannel", "신규", 20, filter);

        // then
        ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
        verify(channelAppender, times(1)).save(captor.capture());
        Channel saved = captor.getValue();
        assertThat(saved.getExternalChannelId()).isEqualTo("UCnewchannel");
        assertThat(saved.getUploadsPlaylistId()).isEqualTo("UUnewchannel");
        assertThat(saved.getName()).isEqualTo("신규");
        assertThat(saved.getPlatform()).isEqualTo(Platform.YOUTUBE);
    }

    @Test
    @DisplayName("[기동 시 5개 기본 채널을 시딩하고, 프로필 사진이 없는 채널이 없으면 유튜브 API를 호출하지 않는다]")
    void run_seedsFiveChannels_noImageBackfillWhenNothingMissing() {
        given(channelReader.existsByPlatformAndExternalChannelId(any(), anyString())).willReturn(false);
        given(channelReader.findByPlatformAndProfileImageUrlIsNull(Platform.YOUTUBE)).willReturn(List.of());

        initializer.run(null);

        ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
        verify(channelAppender, times(5)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Channel::getName)
            .containsExactly("한국경제TV", "런던고라니", "주덕", "알상무", "미과장");
        verify(youtubeApiClient, never()).getChannels(anyList());
    }

    @Test
    @DisplayName("[프로필 사진이 비어 있는 유튜브 채널은 한 번에 조회해 URL을 채워 저장한다]")
    void run_backfillsMissingProfileImages() {
        // given
        Channel missing = youtubeChannel("UCmissing01", "사진없음");
        given(channelReader.existsByPlatformAndExternalChannelId(any(), anyString())).willReturn(true);
        given(channelReader.findByPlatformAndProfileImageUrlIsNull(Platform.YOUTUBE)).willReturn(List.of(missing));
        given(youtubeApiClient.getChannels(List.of("UCmissing01"))).willReturn(thumbnails("UCmissing01", "https://img/1.png"));
        given(channelReader.findByPlatformAndExternalChannelId(Platform.YOUTUBE, "UCmissing01"))
            .willReturn(Optional.of(missing));

        // when
        initializer.run(null);

        // then
        assertThat(missing.getProfileImageUrl()).isEqualTo("https://img/1.png");
        verify(channelAppender).save(missing);
    }

    @Test
    @DisplayName("[썸네일 정보가 없는 항목은 건너뛴다]")
    void run_skipsItemsWithoutThumbnail() {
        Channel missing = youtubeChannel("UCmissing01", "사진없음");
        given(channelReader.existsByPlatformAndExternalChannelId(any(), anyString())).willReturn(true);
        given(channelReader.findByPlatformAndProfileImageUrlIsNull(Platform.YOUTUBE)).willReturn(List.of(missing));
        given(youtubeApiClient.getChannels(anyList())).willReturn(new YoutubeChannelsResponse(
            List.of(new YoutubeChannelsResponse.Item("UCmissing01", new YoutubeChannelsResponse.Snippet(null)))));

        initializer.run(null);

        assertThat(missing.getProfileImageUrl()).isNull();
        verify(channelAppender, never()).save(missing);
    }

    @Test
    @DisplayName("[유튜브 API 장애로 사진 백필이 실패해도 앱 기동을 막지 않는다(다음 기동에 재시도)]")
    void run_backfillFailure_doesNotBlockStartup() {
        Channel missing = youtubeChannel("UCmissing01", "사진없음");
        given(channelReader.existsByPlatformAndExternalChannelId(any(), anyString())).willReturn(true);
        given(channelReader.findByPlatformAndProfileImageUrlIsNull(Platform.YOUTUBE)).willReturn(List.of(missing));
        willThrow(new IllegalStateException("quota exceeded")).given(youtubeApiClient).getChannels(anyList());

        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
        verify(channelReader, never()).findByPlatformAndExternalChannelId(any(), eq("UCmissing01"));
    }
}
