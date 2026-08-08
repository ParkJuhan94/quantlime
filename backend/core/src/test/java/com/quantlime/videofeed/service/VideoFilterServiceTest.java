package com.quantlime.videofeed.service;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.repository.VideoRepository;
import java.math.BigDecimal;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class VideoFilterServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @InjectMocks
    private VideoFilterService videoFilterService;

    @Test
    @DisplayName("[보존 기간(14일)을 이미 넘긴 영상은 다른 필터보다 먼저 FILTERED_OUT 처리한다(2026-08-09 - "
        + "삭제된 옛날 영상이 재수집→재처리되며 API 호출을 낭비하고 최근 영상의 배치 순번을 밀어내던 문제 수정)]")
    void applyFilters_alreadyPastRetentionWindow_filtersOutBeforeOtherChecks() {
        // given: velocity_multiplier=0(개인 채널)이라 하드필터만 통과하면 원래는
        // 바로 SELECTED됐어야 할 조건이지만, 발행일이 보존기간(14일)을 넘겨 있다.
        Channel channel = channelOf(new ChannelFilterConfig(180, 0.0, 5, List.of(), List.of()));
        Video video = videoOf(channel, "오래된 영상", 400, 9999L, LocalDateTime.now().minusDays(15));
        given(videoRepository.findByChannelAndStatus(channel, VideoStatus.DISCOVERED)).willReturn(List.of(video));

        // when
        videoFilterService.applyFilters(channel);

        // then
        assertThat(video.getStatus()).isEqualTo(VideoStatus.FILTERED_OUT);
    }

    @Test
    @DisplayName("[제목 제외 키워드가 포함된 영상은 FILTERED_OUT 처리한다]")
    void applyFilters_titleExcludeMatch_filtersOut() {
        // given
        Channel channel = channelOf(new ChannelFilterConfig(300, 0.0, 5, List.of("속보"), List.of()));
        Video video = videoOf(channel, "속보) 삼성전자 급등", 400, 100L, LocalDateTime.now().minusDays(1));
        given(videoRepository.findByChannelAndStatus(channel, VideoStatus.DISCOVERED)).willReturn(List.of(video));

        // when
        videoFilterService.applyFilters(channel);

        // then
        assertThat(video.getStatus()).isEqualTo(VideoStatus.FILTERED_OUT);
    }

    @Test
    @DisplayName("[최소 영상 길이 미만이면 FILTERED_OUT 처리한다]")
    void applyFilters_belowMinDuration_filtersOut() {
        // given
        Channel channel = channelOf(new ChannelFilterConfig(300, 0.0, 5, List.of(), List.of()));
        Video video = videoOf(channel, "짧은 영상", 60, 100L, LocalDateTime.now().minusDays(1));
        given(videoRepository.findByChannelAndStatus(channel, VideoStatus.DISCOVERED)).willReturn(List.of(video));

        // when
        videoFilterService.applyFilters(channel);

        // then
        assertThat(video.getStatus()).isEqualTo(VideoStatus.FILTERED_OUT);
    }

    @Test
    @DisplayName("[velocity_multiplier가 0이면 velocity 판정 없이 바로 선정 후보가 된다]")
    void applyFilters_zeroVelocityMultiplier_skipsVelocityCheck() {
        // given
        Channel channel = channelOf(new ChannelFilterConfig(180, 0.0, 5, List.of(), List.of()));
        Video video = videoOf(channel, "개인 채널 영상", 400, 10L, LocalDateTime.now().minusHours(1));
        given(videoRepository.findByChannelAndStatus(channel, VideoStatus.DISCOVERED)).willReturn(List.of(video));

        // when
        videoFilterService.applyFilters(channel);

        // then
        assertThat(video.getStatus()).isEqualTo(VideoStatus.SELECTED);
    }

    @Test
    @DisplayName("[업로드 6시간 이내면서 velocity 판정 대상이면 PENDING_REVIEW로 유예한다]")
    void applyFilters_withinGracePeriod_pendingReview() {
        // given
        Channel channel = channelOf(new ChannelFilterConfig(300, 1.5, 5, List.of(), List.of()));
        Video video = videoOf(channel, "방금 올라온 영상", 400, 10L, LocalDateTime.now().minusHours(1));
        given(videoRepository.findByChannelAndStatus(channel, VideoStatus.DISCOVERED)).willReturn(List.of(video));

        // when
        videoFilterService.applyFilters(channel);

        // then
        assertThat(video.getStatus()).isEqualTo(VideoStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("[6시간 경과 후 중앙값*배수보다 조회 속도가 낮으면 FILTERED_OUT 처리한다]")
    void applyFilters_belowVelocityThreshold_filtersOut() {
        // given
        Channel channel = channelOf(new ChannelFilterConfig(300, 1.5, 5, List.of(), List.of()));
        channel.updateMedianVelocity(BigDecimal.valueOf(100));
        Video video = videoOf(channel, "저조한 영상", 400, 50L, LocalDateTime.now().minusHours(10));
        given(videoRepository.findByChannelAndStatus(channel, VideoStatus.DISCOVERED)).willReturn(List.of(video));

        // when
        videoFilterService.applyFilters(channel);

        // then
        assertThat(video.getStatus()).isEqualTo(VideoStatus.FILTERED_OUT);
    }

    @Test
    @DisplayName("[6시간 경과 후 중앙값*배수 이상이면 선정 후보가 된다]")
    void applyFilters_aboveVelocityThreshold_selects() {
        // given
        Channel channel = channelOf(new ChannelFilterConfig(300, 1.5, 5, List.of(), List.of()));
        channel.updateMedianVelocity(BigDecimal.valueOf(10));
        Video video = videoOf(channel, "인기 영상", 400, 1000L, LocalDateTime.now().minusHours(10));
        given(videoRepository.findByChannelAndStatus(channel, VideoStatus.DISCOVERED)).willReturn(List.of(video));

        // when
        videoFilterService.applyFilters(channel);

        // then
        assertThat(video.getStatus()).isEqualTo(VideoStatus.SELECTED);
    }

    @Test
    @DisplayName("[선정 후보가 max_per_run을 넘으면 조회수 하위 영상은 FILTERED_OUT 처리한다]")
    void applyFilters_exceedsMaxPerRun_filtersOutOverflow() {
        // given
        Channel channel = channelOf(new ChannelFilterConfig(180, 0.0, 1, List.of(), List.of()));
        Video popular = videoOf(channel, "인기 영상", 400, 1000L, LocalDateTime.now().minusHours(1));
        Video lessPopular = videoOf(channel, "비인기 영상", 400, 10L, LocalDateTime.now().minusHours(1));
        given(videoRepository.findByChannelAndStatus(channel, VideoStatus.DISCOVERED))
            .willReturn(List.of(lessPopular, popular));

        // when
        videoFilterService.applyFilters(channel);

        // then
        assertThat(popular.getStatus()).isEqualTo(VideoStatus.SELECTED);
        assertThat(lessPopular.getStatus()).isEqualTo(VideoStatus.FILTERED_OUT);
    }

    @Test
    @DisplayName("[max_per_run은 수집 사이클이 아니라 발행일 기준 하루 단위로 적용된다(2026-08-08 버그 수정)]")
    void applyFilters_maxPerRunAppliesPerPublishedDateNotPerRun() {
        // given: 서로 다른 이틀(오늘/어제)에 발행된 영상이 한 사이클(예: 며칠
        // 건너뛴 뒤 몰아서 도는 로컬 개발 수집)에 함께 후보로 잡힌 상황.
        // max_per_run=1이라도 날짜가 다르면 각 날짜에서 독립적으로 1개씩,
        // 총 2개가 선정돼야 한다 - 사이클 전체에서 1개만 남으면 버그.
        Channel channel = channelOf(new ChannelFilterConfig(180, 0.0, 1, List.of(), List.of()));
        Video today = videoOf(channel, "오늘 영상", 400, 1000L, LocalDateTime.now());
        Video yesterday = videoOf(channel, "어제 영상", 400, 1000L, LocalDateTime.now().minusDays(1));
        given(videoRepository.findByChannelAndStatus(channel, VideoStatus.DISCOVERED))
            .willReturn(List.of(today, yesterday));

        // when
        videoFilterService.applyFilters(channel);

        // then
        assertThat(today.getStatus()).isEqualTo(VideoStatus.SELECTED);
        assertThat(yesterday.getStatus()).isEqualTo(VideoStatus.SELECTED);
    }

    @Test
    @DisplayName("[해당 날짜에 이미 max_per_run만큼 선택돼 있으면 남은 쿼터가 0이라 신규 후보는 전부 컷된다]")
    void applyFilters_dailyQuotaAlreadyExhausted_filtersOutAllNewCandidates() {
        // given: DB에 그 날짜 SELECTED가 이미 1건 있다고 가정(다른 사이클에서
        // 먼저 선택된 영상) - max_per_run=1이므로 이번 배치의 신규 후보는
        // 조회수와 무관하게 전부 FILTERED_OUT돼야 한다.
        Channel channel = channelOf(new ChannelFilterConfig(180, 0.0, 1, List.of(), List.of()));
        Video video = videoOf(channel, "이번 사이클 신규 영상", 400, 9999L, LocalDateTime.now());
        given(videoRepository.findByChannelAndStatus(channel, VideoStatus.DISCOVERED)).willReturn(List.of(video));
        given(videoRepository.countByChannelAndStatusAndPublishedAtBetween(eq(channel), eq(VideoStatus.SELECTED), any(), any()))
            .willReturn(1);

        // when
        videoFilterService.applyFilters(channel);

        // then
        assertThat(video.getStatus()).isEqualTo(VideoStatus.FILTERED_OUT);
    }

    @Test
    @DisplayName("[재평가 대상 조회는 같은 채널의 PENDING_REVIEW 영상만 골라낸다]")
    void findReevaluationCandidates_filtersByChannel() {
        // given
        Channel channel = channelOf(new ChannelFilterConfig(300, 1.5, 5, List.of(), List.of()));
        ReflectionTestUtils.setField(channel, "id", 1L);
        Channel otherChannel = channelOf(new ChannelFilterConfig(300, 1.5, 5, List.of(), List.of()));
        ReflectionTestUtils.setField(otherChannel, "id", 2L);

        Video ownVideo = videoOf(channel, "이 채널 영상", 400, 10L, LocalDateTime.now().minusHours(10));
        Video otherChannelVideo = videoOf(otherChannel, "다른 채널 영상", 400, 10L, LocalDateTime.now().minusHours(10));
        given(videoRepository.findByStatusAndPublishedAtBefore(eq(VideoStatus.PENDING_REVIEW), any()))
            .willReturn(List.of(ownVideo, otherChannelVideo));

        // when
        List<Video> candidates = videoFilterService.findReevaluationCandidates(channel);

        // then
        assertThat(candidates).containsExactly(ownVideo);
    }

    @Test
    @DisplayName("[재평가 시 fresh view count가 있으면 그 값으로 갱신 후 재분류한다(2026-08-02 버그 수정)]")
    void reevaluatePendingReview_withFreshViewCount_updatesAndReclassifies() {
        // given: DB에 남은 view_count(10)로는 velocity 미달이지만, 새로 받아온
        // view_count(2000)로는 통과하는 상황 - 최초 발견 시점의 낡은
        // view_count를 그대로 쓰면 영구히 FILTERED_OUT됐어야 할 케이스
        Channel channel = channelOf(new ChannelFilterConfig(300, 1.5, 5, List.of(), List.of()));
        channel.updateMedianVelocity(BigDecimal.valueOf(100));
        Video video = videoOf(channel, "재평가 대상 영상", 400, 10L, LocalDateTime.now().minusHours(10));
        ReflectionTestUtils.setField(video, "id", 1L);
        given(videoRepository.findAllById(List.of(1L))).willReturn(List.of(video));

        // when
        videoFilterService.reevaluatePendingReview(
            channel, List.of(1L), Map.of(video.getExternalVideoId(), 2000L));

        // then
        assertThat(video.getViewCount()).isEqualTo(2000L);
        assertThat(video.getStatus()).isEqualTo(VideoStatus.SELECTED);
    }

    @Test
    @DisplayName("[재평가 대상인데 fresh view count가 없으면(삭제/비공개 등) 기존 view_count로 재분류한다]")
    void reevaluatePendingReview_withoutFreshViewCount_fallsBackToExistingViewCount() {
        // given: API가 값을 못 준 영상 - 기존 view_count(500)만으로도 통과되는 상황
        Channel channel = channelOf(new ChannelFilterConfig(300, 1.5, 5, List.of(), List.of()));
        channel.updateMedianVelocity(BigDecimal.valueOf(10));
        Video video = videoOf(channel, "삭제된 영상", 400, 500L, LocalDateTime.now().minusHours(10));
        ReflectionTestUtils.setField(video, "id", 1L);
        given(videoRepository.findAllById(List.of(1L))).willReturn(List.of(video));

        // when
        videoFilterService.reevaluatePendingReview(channel, List.of(1L), Map.of());

        // then
        assertThat(video.getViewCount()).isEqualTo(500L);
        assertThat(video.getStatus()).isEqualTo(VideoStatus.SELECTED);
    }

    @Test
    @DisplayName("[재평가 대상 ID가 없으면 조회/재분류 자체를 하지 않는다]")
    void reevaluatePendingReview_noCandidates_doesNothing() {
        // given
        Channel channel = channelOf(new ChannelFilterConfig(300, 1.5, 5, List.of(), List.of()));

        // when
        videoFilterService.reevaluatePendingReview(channel, List.of(), Map.of());

        // then
        verifyNoInteractions(videoRepository);
    }

    private Channel channelOf(ChannelFilterConfig filterConfig) {
        return Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10, filterConfig);
    }

    private Video videoOf(Channel channel, String title, int durationSec, long viewCount, LocalDateTime publishedAt) {
        return Video.of(channel, "video-" + title.hashCode(), title, publishedAt, durationSec, viewCount, LocalDateTime.now());
    }
}
