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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class VideoFilterServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @InjectMocks
    private VideoFilterService videoFilterService;

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

    private Channel channelOf(ChannelFilterConfig filterConfig) {
        return Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10, filterConfig);
    }

    private Video videoOf(Channel channel, String title, int durationSec, long viewCount, LocalDateTime publishedAt) {
        return Video.of(channel, "video-" + title.hashCode(), title, publishedAt, durationSec, viewCount, LocalDateTime.now());
    }
}
