package com.quantlime.videofeed.service;

import com.quantlime.infra.python.dto.TranscribeApiResponse;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.repository.TranscriptRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import java.time.LocalDateTime;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TranscriptPersistServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private TranscriptRepository transcriptRepository;

    @InjectMocks
    private TranscriptPersistService transcriptPersistService;

    private Video videoOf() {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of()));
        Video video = Video.of(channel, "vid-1", "제목", LocalDateTime.now(), 300, 100L, LocalDateTime.now());
        ReflectionTestUtils.setField(video, "id", 1L);
        return video;
    }

    @Test
    @DisplayName("[자막 조회 성공 시 Transcript를 저장하고 영상을 TRANSCRIBED로 전이한다]")
    void persistResult_available_savesTranscriptAndMarksTranscribed() {
        // given
        Video video = videoOf();
        given(videoRepository.findById(1L)).willReturn(Optional.of(video));
        TranscribeApiResponse response = new TranscribeApiResponse(
            true, "youtube_auto_caption", "ko", "안녕하세요", 5, null);

        // when
        transcriptPersistService.persistResult(1L, response);

        // then
        ArgumentCaptor<com.quantlime.videofeed.domain.Transcript> captor =
            ArgumentCaptor.forClass(com.quantlime.videofeed.domain.Transcript.class);
        verify(transcriptRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("안녕하세요");
        assertThat(captor.getValue().getCharCount()).isEqualTo(5);
        assertThat(video.getStatus()).isEqualTo(VideoStatus.TRANSCRIBED);
    }

    @Test
    @DisplayName("[자막이 없는 영상은 Transcript를 저장하지 않고 사유와 함께 FAILED 처리한다]")
    void persistResult_unavailable_marksFailedWithoutSavingTranscript() {
        // given
        Video video = videoOf();
        given(videoRepository.findById(1L)).willReturn(Optional.of(video));
        TranscribeApiResponse response = new TranscribeApiResponse(
            false, null, null, null, null, "TranscriptsDisabled");

        // when
        transcriptPersistService.persistResult(1L, response);

        // then
        verify(transcriptRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(video.getStatus()).isEqualTo(VideoStatus.FAILED);
        assertThat(video.getFailReason()).contains("TranscriptsDisabled");
        assertThat(video.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[markFetchFailed 호출 시 주어진 사유로 영상을 FAILED 처리한다]")
    void markFetchFailed_marksVideoFailedWithGivenReason() {
        // given
        Video video = videoOf();
        given(videoRepository.findById(1L)).willReturn(Optional.of(video));

        // when
        transcriptPersistService.markFetchFailed(1L, "네트워크 타임아웃");

        // then
        assertThat(video.getStatus()).isEqualTo(VideoStatus.FAILED);
        assertThat(video.getFailReason()).isEqualTo("네트워크 타임아웃");
    }
}
