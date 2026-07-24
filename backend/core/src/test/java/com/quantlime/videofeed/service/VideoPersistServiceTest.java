package com.quantlime.videofeed.service;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.dto.CollectedVideo;
import com.quantlime.videofeed.repository.VideoRepository;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class VideoPersistServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @InjectMocks
    private VideoPersistService videoPersistService;

    @Test
    @DisplayName("[이미 저장된 external_video_id는 건너뛰고 신규 영상만 적재한다(스케줄러 중복 실행 방어)]")
    void upsertAll_skipsExistingVideos() {
        // given
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 0.0, 5, List.of(), List.of()));
        CollectedVideo existing = new CollectedVideo("video-1", "이미 있는 영상", LocalDateTime.now(), 300, 100L);
        CollectedVideo fresh = new CollectedVideo("video-2", "새 영상", LocalDateTime.now(), 300, 200L);
        given(videoRepository.existsByExternalVideoId("video-1")).willReturn(true);
        given(videoRepository.existsByExternalVideoId("video-2")).willReturn(false);

        // when
        int insertedCount = videoPersistService.upsertAll(channel, List.of(existing, fresh));

        // then
        assertThat(insertedCount).isEqualTo(1);
        verify(videoRepository, times(1)).save(org.mockito.ArgumentMatchers.any());
        verify(videoRepository, never()).existsByExternalVideoId("video-3");
    }
}
