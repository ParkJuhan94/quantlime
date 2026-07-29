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
import com.quantlime.videofeed.dto.SummarizeResult;
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
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SummaryCollectionFacadeTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private TranscriptRepository transcriptRepository;

    @Mock
    private PythonEngineClient pythonEngineClient;

    @Mock
    private SummaryPersistService summaryPersistService;

    @InjectMocks
    private SummaryCollectionFacade summaryCollectionFacade;

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
    @DisplayName("[요약 생성 성공 시 결과를 영속화하고 success 결과를 반환한다]")
    void runBatch_success_persistsAndReturnsSuccess() {
        // given
        Video video = videoOf(1L, "제목1");
        given(videoRepository.findSummarizeCandidates(any(), anyInt(), any()))
            .willReturn(new SliceImpl<>(List.of(video)));
        given(transcriptRepository.findByVideo(video)).willReturn(Optional.of(transcriptOf(video, "자막 내용")));
        SummarizeApiResponse response = new SummarizeApiResponse(
            "요약", List.of(), List.of(), "고지", "gemini-2.5-flash", 100, 50);
        given(pythonEngineClient.summarize(new SummarizeApiRequest("제목1", "테스트 채널", "자막 내용")))
            .willReturn(response);

        // when
        List<SummarizeResult> results = summaryCollectionFacade.runBatch();

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        verify(summaryPersistService).persistResult(1L, response);
    }

    @Test
    @DisplayName("[한 영상의 요약 실패가 나머지 배치 처리를 막지 않는다(장애 격리)]")
    void runBatch_oneVideoFails_isolatesFailureAndContinuesBatch() {
        // given
        Video failing = videoOf(1L, "실패영상");
        Video succeeding = videoOf(2L, "성공영상");
        given(videoRepository.findSummarizeCandidates(any(), anyInt(), any()))
            .willReturn(new SliceImpl<>(List.of(failing, succeeding)));
        given(transcriptRepository.findByVideo(failing)).willReturn(Optional.of(transcriptOf(failing, "자막1")));
        given(transcriptRepository.findByVideo(succeeding)).willReturn(Optional.of(transcriptOf(succeeding, "자막2")));
        given(pythonEngineClient.summarize(new SummarizeApiRequest("실패영상", "테스트 채널", "자막1")))
            .willThrow(new ExternalApiException(PythonEngineErrorCode.SUMMARY_GENERATION_FAILED));
        SummarizeApiResponse okResponse = new SummarizeApiResponse(
            "요약", List.of(), List.of(), "고지", "gemini-2.5-flash", 100, 50);
        given(pythonEngineClient.summarize(new SummarizeApiRequest("성공영상", "테스트 채널", "자막2")))
            .willReturn(okResponse);

        // when
        List<SummarizeResult> results = summaryCollectionFacade.runBatch();

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(1).success()).isTrue();
        verify(summaryPersistService).markSummarizeFailed(org.mockito.ArgumentMatchers.eq(1L), any());
        verify(summaryPersistService).persistResult(2L, okResponse);
    }
}
