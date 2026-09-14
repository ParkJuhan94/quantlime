package com.quantlime.videofeed.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.SummarizeApiRequest;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.infra.python.exception.PythonEngineErrorCode;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.repository.TranscriptRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SummaryProcessingServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private TranscriptRepository transcriptRepository;

    @Mock
    private PythonEngineClient pythonEngineClient;

    @Mock
    private SummaryPersistService summaryPersistService;

    @InjectMocks
    private SummaryProcessingService summaryProcessingService;

    private Video videoOf(Long id, String title) {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of()));
        Video video = Video.of(channel, "vid-" + id, title, LocalDateTime.now(), 300, 100L, LocalDateTime.now());
        ReflectionTestUtils.setField(video, "id", id);
        return video;
    }

    private Transcript transcriptOf(Video video, String content) {
        return Transcript.of(video, "youtube_auto_caption", "ko", content, content.length());
    }

    @Test
    @DisplayName("[요약 생성 성공 시 결과를 영속화한다]")
    void processVideo_success_persistsResult() {
        // given
        Video video = videoOf(1L, "제목1");
        given(videoRepository.findByIdWithChannel(1L)).willReturn(Optional.of(video));
        given(transcriptRepository.findByVideo(video)).willReturn(Optional.of(transcriptOf(video, "자막 내용")));
        SummarizeApiResponse response = new SummarizeApiResponse(
            "요약", List.of(), List.of(), List.of(), "고지", "gemini-3.5-flash-lite", 100, 50);
        given(pythonEngineClient.summarize(new SummarizeApiRequest("제목1", "테스트 채널", "자막 내용")))
            .willReturn(response);

        // when
        summaryProcessingService.processVideo(1L);

        // then
        verify(summaryPersistService).persistResult(1L, response);
    }

    @Test
    @DisplayName("[실패 시 markSummarizeFailed를 호출하고 예외를 다시 던진다(RetryableTopic이 재시도를 판단해야 하므로)]")
    void processVideo_failure_marksFailedAndRethrows() {
        // given
        Video video = videoOf(1L, "제목1");
        given(videoRepository.findByIdWithChannel(1L)).willReturn(Optional.of(video));
        given(transcriptRepository.findByVideo(video)).willReturn(Optional.of(transcriptOf(video, "자막 내용")));
        given(pythonEngineClient.summarize(any()))
            .willThrow(new ExternalApiException(PythonEngineErrorCode.SUMMARY_GENERATION_FAILED));

        // when / then
        assertThatThrownBy(() -> summaryProcessingService.processVideo(1L))
            .isInstanceOf(ExternalApiException.class);
        verify(summaryPersistService).markSummarizeFailed(eq(1L), any());
    }

    @Test
    @DisplayName("[이미 요약이 끝난 영상은 중복 이벤트로 다시 들어와도 스킵한다]")
    void processVideo_alreadySummarized_skipsAsNoOp() {
        // given
        Video video = videoOf(1L, "제목1");
        video.markTranscribed();
        video.markSummarized();
        given(videoRepository.findByIdWithChannel(1L)).willReturn(Optional.of(video));

        // when
        summaryProcessingService.processVideo(1L);

        // then
        verifyNoInteractions(pythonEngineClient);
        verifyNoInteractions(summaryPersistService);
    }

    @Test
    @DisplayName("[영상이 이미 삭제된 경우(보존기간 정리 등) 아무 것도 하지 않는다]")
    void processVideo_videoNotFound_isNoOp() {
        // given
        given(videoRepository.findByIdWithChannel(1L)).willReturn(Optional.empty());

        // when
        summaryProcessingService.processVideo(1L);

        // then
        verifyNoInteractions(pythonEngineClient);
        verifyNoInteractions(summaryPersistService);
    }
}
