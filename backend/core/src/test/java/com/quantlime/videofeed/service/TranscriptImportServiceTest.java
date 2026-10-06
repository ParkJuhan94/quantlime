package com.quantlime.videofeed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.sync.dto.TranscriptImportRequest;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.dto.TranscriptImportResult;
import com.quantlime.videofeed.dto.TranscriptImportResult.Outcome;
import com.quantlime.videofeed.implement.TranscriptAppender;
import com.quantlime.videofeed.implement.VideoReader;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TranscriptImportServiceTest {

    @Mock
    private VideoReader videoReader;

    @Mock
    private TranscriptAppender transcriptAppender;

    @InjectMocks
    private TranscriptImportService service;

    private Video videoIn(String externalId, long id, java.util.function.Consumer<Video> statusSetter) {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of()));
        Video video = Video.of(channel, externalId, "제목", LocalDateTime.now(), 300, 100L, LocalDateTime.now());
        ReflectionTestUtils.setField(video, "id", id);
        statusSetter.accept(video);
        return video;
    }

    private TranscriptImportRequest requestOf(String... externalIds) {
        return new TranscriptImportRequest(java.util.Arrays.stream(externalIds)
            .map(id -> new TranscriptImportRequest.Item(id, "youtube", "ko", "본문", 2)).toList());
    }

    private TranscriptImportResult importOne(String externalId) {
        return service.importAll(requestOf(externalId)).get(0);
    }

    @Test
    @DisplayName("[운영 DB에 없는 영상은 NOT_FOUND]")
    void importAll_unknownVideo_notFound() {
        given(videoReader.findByExternalVideoId("x")).willReturn(Optional.empty());

        assertThat(importOne("x").outcome()).isEqualTo(Outcome.NOT_FOUND);
        verify(transcriptAppender, never()).persistResult(any(), any());
    }

    @Test
    @DisplayName("[이미 TRANSCRIBED/SUMMARIZED인 영상은 저장 없이 ALREADY_DONE - 전량 재전송해도 안전한 멱등성]")
    void importAll_alreadyDone_isIdempotent() {
        given(videoReader.findByExternalVideoId("t")).willReturn(Optional.of(videoIn("t", 1L, Video::markTranscribed)));
        given(videoReader.findByExternalVideoId("s")).willReturn(Optional.of(videoIn("s", 2L, v -> {
            v.markTranscribed();
            v.markSummarized();
        })));

        assertThat(importOne("t").outcome()).isEqualTo(Outcome.ALREADY_DONE);
        assertThat(importOne("s").outcome()).isEqualTo(Outcome.ALREADY_DONE);
        verify(transcriptAppender, never()).persistResult(any(), any());
    }

    @Test
    @DisplayName("[SELECTED/FAILED가 아닌 상태(DISCOVERED 등)는 INVALID_STATUS로 거부한다]")
    void importAll_invalidStatus_rejected() {
        given(videoReader.findByExternalVideoId("d")).willReturn(Optional.of(videoIn("d", 1L, v -> { })));

        assertThat(importOne("d").outcome()).isEqualTo(Outcome.INVALID_STATUS);
        verify(transcriptAppender, never()).persistResult(any(), any());
    }

    @Test
    @DisplayName("[SELECTED와 FAILED 영상은 정규 자막 저장 경로(persistResult)에 위임해 IMPORTED]")
    void importAll_selectedAndFailed_areImported() {
        given(videoReader.findByExternalVideoId("sel")).willReturn(Optional.of(videoIn("sel", 1L, Video::markSelected)));
        given(videoReader.findByExternalVideoId("fail"))
            .willReturn(Optional.of(videoIn("fail", 2L, v -> v.markFailed("자막 없음"))));

        assertThat(importOne("sel").outcome()).isEqualTo(Outcome.IMPORTED);
        assertThat(importOne("fail").outcome()).isEqualTo(Outcome.IMPORTED);
        verify(transcriptAppender).persistResult(eq(1L), any());
        verify(transcriptAppender).persistResult(eq(2L), any());
    }

    @Test
    @DisplayName("[판정과 저장 사이에 정규 파이프라인이 먼저 처리해 유니크 제약이 깨지면 500 대신 ALREADY_DONE으로 스킵한다]")
    void importAll_raceOnUniqueConstraint_becomesAlreadyDone() {
        given(videoReader.findByExternalVideoId("race")).willReturn(Optional.of(videoIn("race", 1L, Video::markSelected)));
        willThrow(new DataIntegrityViolationException("uk_transcript")).given(transcriptAppender)
            .persistResult(eq(1L), any());

        assertThat(importOne("race").outcome()).isEqualTo(Outcome.ALREADY_DONE);
    }

    @Test
    @DisplayName("[여러 건은 항목별로 각자 판정한 결과를 입력 순서대로 돌려준다]")
    void importAll_multipleItems_resultsInOrder() {
        given(videoReader.findByExternalVideoId("a")).willReturn(Optional.of(videoIn("a", 1L, Video::markSelected)));
        given(videoReader.findByExternalVideoId("b")).willReturn(Optional.empty());

        List<TranscriptImportResult> results = service.importAll(requestOf("a", "b"));

        assertThat(results).extracting(TranscriptImportResult::outcome)
            .containsExactly(Outcome.IMPORTED, Outcome.NOT_FOUND);
    }
}
