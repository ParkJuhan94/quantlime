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
import com.quantlime.videofeed.repository.ChannelRepository;
import java.util.List;
import java.util.Optional;
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
class ChannelSeedInitializerTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private YoutubeApiClient youtubeApiClient;

    @InjectMocks
    private ChannelSeedInitializer initializer;

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
        given(channelRepository.existsByPlatformAndExternalChannelId(Platform.YOUTUBE, "UCexisting")).willReturn(true);
        given(channelRepository.existsByPlatformAndExternalChannelId(Platform.YOUTUBE, "UCnewchannel")).willReturn(false);
        ChannelFilterConfig filter = new ChannelFilterConfig(180, 0.0, 3, List.of(), List.of());

        // when
        initializer.seedIfAbsent("UCexisting", "기존", 10, filter);
        initializer.seedIfAbsent("UCnewchannel", "신규", 20, filter);

        // then
        ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository, times(1)).save(captor.capture());
        Channel saved = captor.getValue();
        assertThat(saved.getExternalChannelId()).isEqualTo("UCnewchannel");
        assertThat(saved.getUploadsPlaylistId()).isEqualTo("UUnewchannel");
        assertThat(saved.getName()).isEqualTo("신규");
        assertThat(saved.getPlatform()).isEqualTo(Platform.YOUTUBE);
    }

    @Test
    @DisplayName("[기동 시 5개 기본 채널을 시딩하고, 프로필 사진이 없는 채널이 없으면 유튜브 API를 호출하지 않는다]")
    void run_seedsFiveChannels_noImageBackfillWhenNothingMissing() {
        given(channelRepository.existsByPlatformAndExternalChannelId(any(), anyString())).willReturn(false);
        given(channelRepository.findByPlatformAndProfileImageUrlIsNull(Platform.YOUTUBE)).willReturn(List.of());

        initializer.run(null);

        ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository, times(5)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Channel::getName)
            .containsExactly("한국경제TV", "런던고라니", "주덕", "알상무", "미과장");
        verify(youtubeApiClient, never()).getChannels(anyList());
    }

    @Test
    @DisplayName("[프로필 사진이 비어 있는 유튜브 채널은 한 번에 조회해 URL을 채워 저장한다]")
    void run_backfillsMissingProfileImages() {
        // given
        Channel missing = youtubeChannel("UCmissing01", "사진없음");
        given(channelRepository.existsByPlatformAndExternalChannelId(any(), anyString())).willReturn(true);
        given(channelRepository.findByPlatformAndProfileImageUrlIsNull(Platform.YOUTUBE)).willReturn(List.of(missing));
        given(youtubeApiClient.getChannels(List.of("UCmissing01"))).willReturn(thumbnails("UCmissing01", "https://img/1.png"));
        given(channelRepository.findByPlatformAndExternalChannelId(Platform.YOUTUBE, "UCmissing01"))
            .willReturn(Optional.of(missing));

        // when
        initializer.run(null);

        // then
        assertThat(missing.getProfileImageUrl()).isEqualTo("https://img/1.png");
        verify(channelRepository).save(missing);
    }

    @Test
    @DisplayName("[썸네일 정보가 없는 항목은 건너뛴다]")
    void run_skipsItemsWithoutThumbnail() {
        Channel missing = youtubeChannel("UCmissing01", "사진없음");
        given(channelRepository.existsByPlatformAndExternalChannelId(any(), anyString())).willReturn(true);
        given(channelRepository.findByPlatformAndProfileImageUrlIsNull(Platform.YOUTUBE)).willReturn(List.of(missing));
        given(youtubeApiClient.getChannels(anyList())).willReturn(new YoutubeChannelsResponse(
            List.of(new YoutubeChannelsResponse.Item("UCmissing01", new YoutubeChannelsResponse.Snippet(null)))));

        initializer.run(null);

        assertThat(missing.getProfileImageUrl()).isNull();
        verify(channelRepository, never()).save(missing);
    }

    @Test
    @DisplayName("[유튜브 API 장애로 사진 백필이 실패해도 앱 기동을 막지 않는다(다음 기동에 재시도)]")
    void run_backfillFailure_doesNotBlockStartup() {
        Channel missing = youtubeChannel("UCmissing01", "사진없음");
        given(channelRepository.existsByPlatformAndExternalChannelId(any(), anyString())).willReturn(true);
        given(channelRepository.findByPlatformAndProfileImageUrlIsNull(Platform.YOUTUBE)).willReturn(List.of(missing));
        willThrow(new IllegalStateException("quota exceeded")).given(youtubeApiClient).getChannels(anyList());

        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();
        verify(channelRepository, never()).findByPlatformAndExternalChannelId(any(), eq("UCmissing01"));
    }
}
