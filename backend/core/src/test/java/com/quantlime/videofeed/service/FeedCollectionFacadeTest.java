package com.quantlime.videofeed.service;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FeedCollectionFacadeTest {

    @Mock
    private RedisLockService redisLockService;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private YoutubeVideoCollector youtubeVideoCollector;

    @Mock
    private VideoPersistService videoPersistService;

    @Mock
    private VideoFilterService videoFilterService;

    @InjectMocks
    private FeedCollectionFacade feedCollectionFacade;

    private Channel channelOf(Long id) {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(300, 1.5, 5, List.of(), List.of()));
        ReflectionTestUtils.setField(channel, "id", id);
        return channel;
    }

    private Video videoOf(Channel channel, Long id, String externalVideoId) {
        Video video = Video.of(channel, externalVideoId, "제목", LocalDateTime.now(), 300, 10L, LocalDateTime.now());
        ReflectionTestUtils.setField(video, "id", id);
        return video;
    }

    @Test
    @DisplayName("[재평가 대상이 있으면 최신 조회수를 재조회해 재평가에 넘긴다(2026-08-02 버그 수정 - 이전엔 낡은 view_count로 재평가했음)]")
    void reevaluatePendingReview_withCandidates_fetchesFreshViewCountsBeforeReevaluating() {
        // given
        Channel channel = channelOf(1L);
        given(channelRepository.findByEnabledTrueOrderByPriorityAsc()).willReturn(List.of(channel));
        Video candidate = videoOf(channel, 10L, "vid-pending");
        given(videoFilterService.findReevaluationCandidates(channel)).willReturn(List.of(candidate));
        Map<String, Long> freshViewCounts = Map.of("vid-pending", 5000L);
        given(youtubeVideoCollector.fetchViewCounts(List.of("vid-pending"))).willReturn(freshViewCounts);

        // when
        feedCollectionFacade.reevaluatePendingReview();

        // then
        verify(youtubeVideoCollector).fetchViewCounts(List.of("vid-pending"));
        verify(videoFilterService).reevaluatePendingReview(channel, List.of(10L), freshViewCounts);
    }

    @Test
    @DisplayName("[재평가 대상이 없는 채널은 유튜브 API를 호출하지 않는다]")
    void reevaluatePendingReview_noCandidates_skipsApiCall() {
        // given
        Channel channel = channelOf(1L);
        given(channelRepository.findByEnabledTrueOrderByPriorityAsc()).willReturn(List.of(channel));
        given(videoFilterService.findReevaluationCandidates(channel)).willReturn(List.of());

        // when
        feedCollectionFacade.reevaluatePendingReview();

        // then
        verifyNoInteractions(youtubeVideoCollector);
        verify(videoFilterService, never()).reevaluatePendingReview(any(), any(), any());
    }

    @Test
    @DisplayName("[한 채널의 재평가 실패가 나머지 채널 재평가를 막지 않는다(장애 격리)]")
    void reevaluatePendingReview_oneChannelFails_isolatesFailureAndContinues() {
        // given
        Channel failingChannel = channelOf(1L);
        Channel okChannel = channelOf(2L);
        given(channelRepository.findByEnabledTrueOrderByPriorityAsc())
            .willReturn(List.of(failingChannel, okChannel));
        given(videoFilterService.findReevaluationCandidates(failingChannel))
            .willThrow(new RuntimeException("유튜브 API 장애"));
        Video candidate = videoOf(okChannel, 20L, "vid-ok");
        given(videoFilterService.findReevaluationCandidates(okChannel)).willReturn(List.of(candidate));
        given(youtubeVideoCollector.fetchViewCounts(List.of("vid-ok"))).willReturn(Map.of("vid-ok", 100L));

        // when
        feedCollectionFacade.reevaluatePendingReview();

        // then
        verify(videoFilterService).reevaluatePendingReview(okChannel, List.of(20L), Map.of("vid-ok", 100L));
    }
}
