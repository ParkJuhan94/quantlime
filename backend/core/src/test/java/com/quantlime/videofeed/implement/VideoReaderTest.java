package com.quantlime.videofeed.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

/** Repository 메서드를 올바른 인자 순서로 위임하는지 확인한다(인자 순서가 같은 타입이 많아 바뀌어도 컴파일된다). */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class VideoReaderTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 9, 30, 0, 0);

    @Mock
    private VideoRepository videoRepository;

    @InjectMocks
    private VideoReader reader;

    private final Channel channel = Channel.of(Platform.YOUTUBE, "UCabc", "UUabc", "채널", 10,
        new ChannelFilterConfig(180, 0.0, 5, List.of(), List.of()));
    private final Video video = Video.of(channel, "vid1", "제목", FROM, 600, 10L, FROM);

    @Test
    @DisplayName("[단건 조회류는 같은 이름의 Repository 메서드로 위임한다]")
    void singleLookups_delegate() {
        given(videoRepository.findById(1L)).willReturn(Optional.of(video));
        given(videoRepository.findByIdWithChannel(1L)).willReturn(Optional.of(video));
        given(videoRepository.findByExternalVideoId("vid1")).willReturn(Optional.of(video));
        given(videoRepository.findSummarizedVideoById(1L)).willReturn(Optional.of(video));
        given(videoRepository.findAllById(List.of(1L))).willReturn(List.of(video));

        assertThat(reader.findById(1L)).contains(video);
        assertThat(reader.findByIdWithChannel(1L)).contains(video);
        assertThat(reader.findByExternalVideoId("vid1")).contains(video);
        assertThat(reader.findSummarizedVideoById(1L)).contains(video);
        assertThat(reader.findAllById(List.of(1L))).containsExactly(video);
    }

    @Test
    @DisplayName("[상태·기간 조건 조회는 인자를 순서대로 넘기고 이름이 다른 Repository 메서드로 연결한다]")
    void conditionalLookups_delegateWithArguments() {
        given(videoRepository.findByChannelAndStatus(channel, VideoStatus.DISCOVERED)).willReturn(List.of(video));
        given(videoRepository.countByChannelAndStatusAndPublishedAtBetween(channel, VideoStatus.SUMMARIZED, FROM, TO))
            .willReturn(4);
        given(videoRepository.findByStatusAndPublishedAtBefore(VideoStatus.DISCOVERED, TO)).willReturn(List.of(video));
        given(videoRepository.findIdsByPublishedAtBefore(TO)).willReturn(List.of(1L, 2L));

        assertThat(reader.findByChannelAndStatus(channel, VideoStatus.DISCOVERED)).containsExactly(video);
        assertThat(reader.countPublishedBetween(channel, VideoStatus.SUMMARIZED, FROM, TO)).isEqualTo(4);
        assertThat(reader.findPublishedBefore(VideoStatus.DISCOVERED, TO)).containsExactly(video);
        assertThat(reader.findExpiredIds(TO)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("[후보·피드 조회는 페이징 인자와 함께 위임한다]")
    void pagedLookups_delegate() {
        Pageable pageable = PageRequest.of(0, 20);
        Slice<Video> slice = new SliceImpl<>(List.of(video), pageable, false);
        List<VideoStatus> statuses = List.of(VideoStatus.DISCOVERED);
        given(videoRepository.findTranscribeCandidates(statuses, 3, pageable)).willReturn(slice);
        given(videoRepository.findSummarizeCandidates(statuses, 3, pageable)).willReturn(slice);
        given(videoRepository.findSummarizedVideos("005930", 7L, FROM, TO, pageable)).willReturn(slice);

        assertThat(reader.findTranscribeCandidates(statuses, 3, pageable)).isSameAs(slice);
        assertThat(reader.findSummarizeCandidates(statuses, 3, pageable)).isSameAs(slice);
        assertThat(reader.findSummarizedVideos("005930", 7L, FROM, TO, pageable)).isSameAs(slice);
    }
}
