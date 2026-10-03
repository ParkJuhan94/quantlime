package com.quantlime.videofeed.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.infra.sync.SyncApiClient;
import com.quantlime.infra.sync.SyncProperties;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.dto.request.TranscriptImportRequest;
import com.quantlime.videofeed.implement.ProdTranscriptSyncer;
import com.quantlime.videofeed.implement.TranscriptReader;
import com.quantlime.videofeed.implement.VideoReader;
import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class LocalTranscriptSyncServiceTest {

    @Mock
    private VideoReader videoReader;

    @Mock
    private TranscriptReader transcriptReader;

    @Mock
    private SyncApiClient syncApiClient;

    private LocalTranscriptSyncService service(String apiKey, String prodApiBase) {
        return new LocalTranscriptSyncService(videoReader, transcriptReader,
            new ProdTranscriptSyncer(syncApiClient, new SyncProperties(apiKey, prodApiBase)));
    }

    private Video video() {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of()));
        return Video.of(channel, "vid-1", "제목", LocalDateTime.now(), 300, 100L, LocalDateTime.now());
    }

    @Test
    @DisplayName("[api-key나 prod-api-base가 비어 있으면(운영/미설정) DB 조회도 전송도 하지 않는다]")
    void syncOne_notConfigured_skipsEverything() {
        service("", "https://prod").syncOne(1L);
        service("key", "").syncOne(1L);
        service(null, null).syncOne(1L);

        verifyNoInteractions(videoReader, transcriptReader, syncApiClient);
    }

    @Test
    @DisplayName("[영상이나 자막이 없으면 전송하지 않고 스킵한다]")
    void syncOne_missingVideoOrTranscript_skips() {
        // 영상 없음
        given(videoReader.findById(1L)).willReturn(Optional.empty());
        service("key", "https://prod").syncOne(1L);

        // 영상은 있으나 자막 없음
        Video video = video();
        given(videoReader.findById(2L)).willReturn(Optional.of(video));
        given(transcriptReader.findByVideo(video)).willReturn(Optional.empty());
        service("key", "https://prod").syncOne(2L);

        verify(syncApiClient, never()).pushTranscript(any());
    }

    @Test
    @DisplayName("[영상+자막이 있으면 externalVideoId/출처/언어/본문/글자수를 운영으로 전송한다]")
    void syncOne_pushesTranscriptItem() {
        // given
        Video video = video();
        Transcript transcript = Transcript.of(video, "youtube", "ko", "자막 본문", 5);
        given(videoReader.findById(1L)).willReturn(Optional.of(video));
        given(transcriptReader.findByVideo(video)).willReturn(Optional.of(transcript));

        // when
        service("key", "https://prod").syncOne(1L);

        // then
        ArgumentCaptor<TranscriptImportRequest.Item> captor = ArgumentCaptor.forClass(TranscriptImportRequest.Item.class);
        verify(syncApiClient).pushTranscript(captor.capture());
        TranscriptImportRequest.Item item = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(item.externalVideoId()).isEqualTo("vid-1");
        org.assertj.core.api.Assertions.assertThat(item.source()).isEqualTo("youtube");
        org.assertj.core.api.Assertions.assertThat(item.lang()).isEqualTo("ko");
        org.assertj.core.api.Assertions.assertThat(item.content()).isEqualTo("자막 본문");
        org.assertj.core.api.Assertions.assertThat(item.charCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("[운영이 꺼져 있어 연결이 거부돼도 예외를 삼킨다 - 로컬 자막 파이프라인을 막지 않는 best-effort 경로]")
    void syncOne_connectionRefused_isSwallowed() {
        // given
        Video video = video();
        Transcript transcript = Transcript.of(video, "youtube", "ko", "본문", 2);
        given(videoReader.findById(1L)).willReturn(Optional.of(video));
        given(transcriptReader.findByVideo(video)).willReturn(Optional.of(transcript));
        willThrow(new ResourceAccessException("I/O error", new ConnectException("Connection refused")))
            .given(syncApiClient).pushTranscript(any());

        // when & then
        assertThatCode(() -> service("key", "https://prod").syncOne(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[그 외 예외(운영 5xx 등)도 전파하지 않는다]")
    void syncOne_otherFailure_isSwallowed() {
        Video video = video();
        Transcript transcript = Transcript.of(video, "youtube", "ko", "본문", 2);
        given(videoReader.findById(1L)).willReturn(Optional.of(video));
        given(transcriptReader.findByVideo(video)).willReturn(Optional.of(transcript));
        willThrow(new IllegalStateException("500")).given(syncApiClient).pushTranscript(any());

        assertThatCode(() -> service("key", "https://prod").syncOne(1L)).doesNotThrowAnyException();
    }
}
