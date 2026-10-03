package com.quantlime.videofeed.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.TranscribeApiRequest;
import com.quantlime.infra.python.dto.TranscribeApiResponse;
import com.quantlime.infra.python.exception.PythonEngineErrorCode;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.implement.TranscriptAppender;
import com.quantlime.videofeed.implement.TranscriptFetcher;
import com.quantlime.videofeed.implement.VideoReader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TranscriptProcessingServiceTest {

    @Mock
    private VideoReader videoReader;

    @Mock
    private PythonEngineClient pythonEngineClient;

    @Mock
    private TranscriptAppender transcriptAppender;

    private TranscriptProcessingService transcriptProcessingService;

    // 대상 선정·저장(service)과 외부 호출(implement)을 함께 검증한다 - 외부 클라이언트만 mock.
    @BeforeEach
    void setUpService() {
        transcriptProcessingService = new TranscriptProcessingService(
            videoReader, new TranscriptFetcher(pythonEngineClient), transcriptAppender);
    }

    private Video videoOf(Long id, String externalVideoId) {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of()));
        Video video = Video.of(channel, externalVideoId, "제목", LocalDateTime.now(), 300, 100L, LocalDateTime.now());
        ReflectionTestUtils.setField(video, "id", id);
        return video;
    }

    @Test
    @DisplayName("[자막 조회 성공 시 결과를 영속화한다]")
    void processVideo_available_persistsResult() {
        // given
        Video video = videoOf(1L, "vid-1");
        given(videoReader.findById(1L)).willReturn(Optional.of(video));
        TranscribeApiResponse response = new TranscribeApiResponse(
            true, "youtube_auto_caption", "ko", "내용", 2, null);
        given(pythonEngineClient.fetchTranscript(new TranscribeApiRequest("vid-1"))).willReturn(response);

        // when
        transcriptProcessingService.processVideo(1L);

        // then
        verify(transcriptAppender).persistResult(1L, response);
    }

    @Test
    @DisplayName("[실패 시 markFetchFailed를 호출하고 예외를 다시 던진다(RetryableTopic이 재시도를 판단해야 하므로)]")
    void processVideo_failure_marksFailedAndRethrows() {
        // given
        Video video = videoOf(1L, "vid-1");
        given(videoReader.findById(1L)).willReturn(Optional.of(video));
        given(pythonEngineClient.fetchTranscript(new TranscribeApiRequest("vid-1")))
            .willThrow(new ExternalApiException(PythonEngineErrorCode.TRANSCRIPT_FETCH_FAILED));

        // when / then
        assertThatThrownBy(() -> transcriptProcessingService.processVideo(1L))
            .isInstanceOf(ExternalApiException.class);
        verify(transcriptAppender).markFetchFailed(eq(1L), any());
    }

    @Test
    @DisplayName("[이미 자막 조회가 끝난 영상은 중복 이벤트로 다시 들어와도 스킵한다]")
    void processVideo_alreadyTranscribed_skipsAsNoOp() {
        // given
        Video video = videoOf(1L, "vid-1");
        video.markTranscribed();
        given(videoReader.findById(1L)).willReturn(Optional.of(video));

        // when
        transcriptProcessingService.processVideo(1L);

        // then
        verifyNoInteractions(pythonEngineClient);
        verifyNoInteractions(transcriptAppender);
    }

    @Test
    @DisplayName("[영상이 이미 삭제된 경우(보존기간 정리 등) 아무 것도 하지 않는다]")
    void processVideo_videoNotFound_isNoOp() {
        // given
        given(videoReader.findById(1L)).willReturn(Optional.empty());

        // when
        transcriptProcessingService.processVideo(1L);

        // then
        verifyNoInteractions(pythonEngineClient);
        verifyNoInteractions(transcriptAppender);
    }
}
