package com.quantlime.videofeed.service;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.event.VideoTranscribedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 2026-09-14 이벤트화 이후 이 클래스는 백로그 재발행만 담당한다 - 실제 요약
 * 생성 로직 검증은 {@link SummaryProcessingServiceTest} 참고.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SummaryCollectionFacadeTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SummaryCollectionFacade summaryCollectionFacade;

    private Video videoOf(Long id) {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of()));
        Video video = Video.of(channel, "vid-" + id, "제목", LocalDateTime.now(), 300, 100L, LocalDateTime.now());
        ReflectionTestUtils.setField(video, "id", id);
        return video;
    }

    @Test
    @DisplayName("[후보 영상마다 VideoTranscribedEvent를 재발행하고 발행 건수를 반환한다]")
    void publishBacklog_publishesEventPerCandidateAndReturnsCount() {
        // given
        Video first = videoOf(1L);
        Video second = videoOf(2L);
        given(videoRepository.findSummarizeCandidates(any(), anyInt(), any()))
            .willReturn(new SliceImpl<>(List.of(first, second)));

        // when
        int published = summaryCollectionFacade.publishBacklog();

        // then
        assertThat(published).isEqualTo(2);
        verify(eventPublisher).publishEvent(new VideoTranscribedEvent(1L));
        verify(eventPublisher).publishEvent(new VideoTranscribedEvent(2L));
    }

    @Test
    @DisplayName("[후보가 없으면 이벤트를 발행하지 않고 0을 반환한다]")
    void publishBacklog_noCandidates_publishesNothing() {
        // given
        given(videoRepository.findSummarizeCandidates(any(), anyInt(), any()))
            .willReturn(new SliceImpl<>(List.of()));

        // when
        int published = summaryCollectionFacade.publishBacklog();

        // then
        assertThat(published).isZero();
        verifyNoInteractions(eventPublisher);
    }
}
