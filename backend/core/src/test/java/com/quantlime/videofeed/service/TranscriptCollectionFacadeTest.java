package com.quantlime.videofeed.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.TranscribeApiRequest;
import com.quantlime.infra.python.dto.TranscribeApiResponse;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.dto.TranscribeResult;
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
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TranscriptCollectionFacadeTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private PythonEngineClient pythonEngineClient;

    @Mock
    private TranscriptPersistService transcriptPersistService;

    @InjectMocks
    private TranscriptCollectionFacade transcriptCollectionFacade;

    private Video videoOf(Long id, String externalVideoId) {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of()));
        Video video = Video.of(channel, externalVideoId, "제목", LocalDateTime.now(), 300, 100L, LocalDateTime.now());
        ReflectionTestUtils.setField(video, "id", id);
        return video;
    }

    @Test
    @DisplayName("[자막 조회 성공 시 결과를 영속화하고 success 결과를 반환한다]")
    void runBatch_available_persistsAndReturnsSuccess() {
        // given
        Video video = videoOf(1L, "vid-1");
        given(videoRepository.findByStatusInAndRetryCountLessThanOrderByPublishedAtAsc(any(), anyInt(), any()))
            .willReturn(new SliceImpl<>(List.of(video)));
        TranscribeApiResponse response = new TranscribeApiResponse(
            true, "youtube_auto_caption", "ko", "내용", 2, null);
        given(pythonEngineClient.fetchTranscript(new TranscribeApiRequest("vid-1"))).willReturn(response);

        // when
        List<TranscribeResult> results = transcriptCollectionFacade.runBatch();

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).available()).isTrue();
        verify(transcriptPersistService).persistResult(1L, response);
    }

    @Test
    @DisplayName("[자막이 없는 영상은 결과를 영속화하되 unavailable 결과를 반환한다]")
    void runBatch_unavailable_persistsAndReturnsUnavailable() {
        // given
        Video video = videoOf(1L, "vid-1");
        given(videoRepository.findByStatusInAndRetryCountLessThanOrderByPublishedAtAsc(any(), anyInt(), any()))
            .willReturn(new SliceImpl<>(List.of(video)));
        TranscribeApiResponse response = new TranscribeApiResponse(
            false, null, null, null, null, "TranscriptsDisabled");
        given(pythonEngineClient.fetchTranscript(new TranscribeApiRequest("vid-1"))).willReturn(response);

        // when
        List<TranscribeResult> results = transcriptCollectionFacade.runBatch();

        // then
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).available()).isFalse();
        assertThat(results.get(0).reason()).isEqualTo("TranscriptsDisabled");
        verify(transcriptPersistService).persistResult(1L, response);
    }

    @Test
    @DisplayName("[한 영상의 조회 실패가 나머지 배치 처리를 막지 않는다(장애 격리)]")
    void runBatch_oneVideoFails_isolatesFailureAndContinuesBatch() {
        // given
        Video failing = videoOf(1L, "vid-fail");
        Video succeeding = videoOf(2L, "vid-ok");
        given(videoRepository.findByStatusInAndRetryCountLessThanOrderByPublishedAtAsc(any(), anyInt(), any()))
            .willReturn(new SliceImpl<>(List.of(failing, succeeding)));
        given(pythonEngineClient.fetchTranscript(new TranscribeApiRequest("vid-fail")))
            .willThrow(new ExternalApiException(com.quantlime.infra.python.exception.PythonEngineErrorCode.TRANSCRIPT_FETCH_FAILED));
        TranscribeApiResponse okResponse = new TranscribeApiResponse(
            true, "youtube_auto_caption", "ko", "내용", 2, null);
        given(pythonEngineClient.fetchTranscript(new TranscribeApiRequest("vid-ok"))).willReturn(okResponse);

        // when
        List<TranscribeResult> results = transcriptCollectionFacade.runBatch();

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(1).success()).isTrue();
        verify(transcriptPersistService).markFetchFailed(org.mockito.ArgumentMatchers.eq(1L), any());
        verify(transcriptPersistService).persistResult(2L, okResponse);
    }
}
