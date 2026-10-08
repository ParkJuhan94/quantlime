package com.quantlime.videofeed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.dto.CollectResult;
import com.quantlime.videofeed.dto.CollectedVideo;
import com.quantlime.videofeed.implement.ChannelAppender;
import com.quantlime.videofeed.implement.ChannelReader;
import com.quantlime.videofeed.implement.VideoAppender;
import com.quantlime.videofeed.implement.YoutubeVideoCollector;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FeedCollectionFacadeTest {

    @Mock
    private RedisLockService redisLockService;

    @Mock
    private ChannelReader channelReader;

    @Mock
    private ChannelAppender channelAppender;

    @Mock
    private YoutubeVideoCollector youtubeVideoCollector;

    @Mock
    private VideoAppender videoAppender;

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
        given(channelReader.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.YOUTUBE)).willReturn(List.of(channel));
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
        given(channelReader.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.YOUTUBE)).willReturn(List.of(channel));
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
        given(channelReader.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.YOUTUBE))
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

    @Test
    @DisplayName("[채널 수집 성공 - 적재 건수를 결과에 담고 필터 적용 후 마지막 수집 시각을 저장한다]")
    void runAll_success_upsertsFiltersAndStampsLastCollectedAt() {
        // given
        Channel channel = channelOf(1L);
        given(channelReader.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.YOUTUBE)).willReturn(List.of(channel));
        List<CollectedVideo> collected = List.of(mock(CollectedVideo.class), mock(CollectedVideo.class));
        given(youtubeVideoCollector.collect(channel)).willReturn(collected);
        given(videoAppender.upsertAll(channel, collected)).willReturn(2);
        given(channelReader.findById(1L)).willReturn(Optional.of(channel));

        // when
        List<CollectResult> results = feedCollectionFacade.runAll();

        // then
        assertThat(results).containsExactly(CollectResult.success("테스트 채널", 2));
        verify(videoFilterService).applyFilters(channel);
        verify(channelAppender).save(channel);
        assertThat(channel.getLastCollectedAt()).isNotNull();
    }

    @Test
    @DisplayName("[한 채널의 수집 실패는 실패 결과로 남기고 다음 채널 수집을 계속한다]")
    void runAll_oneChannelFails_isolatesFailure() {
        // given
        Channel failing = channelOf(1L);
        Channel ok = channelOf(2L);
        given(channelReader.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.YOUTUBE))
            .willReturn(List.of(failing, ok));
        given(youtubeVideoCollector.collect(failing)).willThrow(new IllegalStateException("쿼터 초과"));
        given(youtubeVideoCollector.collect(ok)).willReturn(List.of());
        given(videoAppender.upsertAll(ok, List.of())).willReturn(0);
        given(channelReader.findById(2L)).willReturn(Optional.of(ok));

        // when
        List<CollectResult> results = feedCollectionFacade.runAll();

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).errorMessage()).isEqualTo("쿼터 초과");
        assertThat(results.get(1).success()).isTrue();
        verify(videoFilterService, never()).applyFilters(failing);
    }

    @Test
    @DisplayName("[수집 직후 채널이 사라졌으면 마지막 수집 시각을 저장하지 않고 실패로 기록한다]")
    void runAll_channelVanishedBeforeStamp_recordsFailure() {
        // given
        Channel channel = channelOf(1L);
        given(channelReader.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.YOUTUBE)).willReturn(List.of(channel));
        given(youtubeVideoCollector.collect(channel)).willReturn(List.of());
        given(channelReader.findById(1L)).willReturn(Optional.empty());

        // when
        List<CollectResult> results = feedCollectionFacade.runAll();

        // then
        assertThat(results).singleElement().satisfies(r -> assertThat(r.success()).isFalse());
        verify(channelAppender, never()).save(any());
    }

    @Test
    @DisplayName("[배타 실행 - 락을 잡으면 수집과 PENDING_REVIEW 재평가를 한 구간에서 실행한다]")
    void runAllExclusively_lockAcquired_runsCollectAndReevaluate() {
        // given
        given(redisLockService.runExclusively(eq("lock:feed-collect"), any(Duration.class), any()))
            .willAnswer(invocation -> Optional.of(((Supplier<?>) invocation.getArgument(2)).get()));
        given(channelReader.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.YOUTUBE)).willReturn(List.of());

        // when
        Optional<List<CollectResult>> result = feedCollectionFacade.runAllExclusively();

        // then: 수집(runAll)과 재평가가 각각 채널 목록을 한 번씩 조회한다
        assertThat(result).contains(List.of());
        verify(channelReader, times(2)).findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.YOUTUBE);
    }

    @Test
    @DisplayName("[배타 실행 - 다른 인스턴스가 락을 쥐고 있으면 아무것도 실행하지 않고 빈 값을 돌려준다]")
    void runAllExclusively_lockHeldElsewhere_returnsEmpty() {
        // given
        given(redisLockService.runExclusively(eq("lock:feed-collect"), any(Duration.class), any()))
            .willReturn(Optional.empty());

        // when
        Optional<List<CollectResult>> result = feedCollectionFacade.runAllExclusively();

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(channelReader, youtubeVideoCollector);
    }
}
