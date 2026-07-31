package com.quantlime.videofeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Summary;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoTicker;
import com.quantlime.videofeed.dto.response.VideoFeedDetailResponse;
import com.quantlime.videofeed.dto.response.VideoFeedItemResponse;
import com.quantlime.videofeed.repository.SummaryRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import com.quantlime.videofeed.repository.VideoTickerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class VideoFeedServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private SummaryRepository summaryRepository;

    @Mock
    private VideoTickerRepository videoTickerRepository;

    private VideoFeedService videoFeedService;

    private Video videoOf(Long id, String title) {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of()));
        Video video = Video.of(channel, "vid-" + id, title, LocalDateTime.now(), 300, 100L, LocalDateTime.now());
        ReflectionTestUtils.setField(video, "id", id);
        return video;
    }

    private Summary summaryOf(Video video, String summaryText) {
        String payload = "{\"summary\":\"" + summaryText
            + "\",\"key_points\":[\"포인트1\",\"포인트2\"],"
            + "\"mentioned_tickers\":[],\"caveat\":\"투자 권유 아님\"}";
        Summary summary = Summary.of(video, "gemini-3.5-flash-lite", payload, 100, 50);
        ReflectionTestUtils.setField(summary, "id", video.getId());
        return summary;
    }

    // ObjectMapper는 실제 빈을 그대로 new해서 쓴다 - JSON 역직렬화 자체가
    // 이 서비스의 핵심 동작이라 mock으로 대체하면 검증 의미가 없다.
    private VideoFeedService newService() {
        return new VideoFeedService(videoRepository, summaryRepository, videoTickerRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("[tickerCode/date가 없으면 전체 요약 영상을 최신순으로 조회하고, 각 영상의 요약문/태깅 종목을 배치로 채운다]")
    void getVideos_withoutFilters_returnsAllSummarizedVideos() {
        // given
        videoFeedService = newService();
        Video video1 = videoOf(1L, "영상1");
        Video video2 = videoOf(2L, "영상2");
        Pageable pageable = PageRequest.of(0, 10);
        given(videoRepository.findSummarizedVideos(null, null, null, pageable))
            .willReturn(new SliceImpl<>(List.of(video1, video2)));
        given(summaryRepository.findByVideo_IdIn(List.of(1L, 2L)))
            .willReturn(List.of(summaryOf(video1, "요약1"), summaryOf(video2, "요약2")));
        given(videoTickerRepository.findByVideo_IdIn(List.of(1L, 2L)))
            .willReturn(List.of(VideoTicker.of(video1, "005930", "삼성전자", "BULLISH", BigDecimal.valueOf(0.8))));

        // when
        Slice<VideoFeedItemResponse> result = videoFeedService.getVideos(null, null, pageable);

        // then
        assertThat(result.getContent()).hasSize(2);
        VideoFeedItemResponse item1 = result.getContent().get(0);
        assertThat(item1.summary()).isEqualTo("요약1");
        assertThat(item1.tickers()).hasSize(1);
        assertThat(item1.tickers().get(0).tickerCode()).isEqualTo("005930");
        assertThat(item1.videoUrl()).isEqualTo("https://www.youtube.com/watch?v=vid-1");
        assertThat(item1.channelUrl()).isEqualTo("https://www.youtube.com/channel/UCtest");

        VideoFeedItemResponse item2 = result.getContent().get(1);
        assertThat(item2.summary()).isEqualTo("요약2");
        assertThat(item2.tickers()).isEmpty();
    }

    @Test
    @DisplayName("[tickerCode/date를 지정하면 하루 범위(00:00~다음날 00:00)와 함께 리포지토리에 그대로 넘긴다]")
    void getVideos_withTickerCodeAndDate_passesDayRangeToRepository() {
        // given
        videoFeedService = newService();
        Pageable pageable = PageRequest.of(0, 10);
        Slice<Video> emptySlice = new SliceImpl<>(List.of());
        LocalDate date = LocalDate.of(2026, 7, 30);
        given(videoRepository.findSummarizedVideos(
            eq("005930"), eq(date.atStartOfDay()), eq(date.plusDays(1).atStartOfDay()), eq(pageable)))
            .willReturn(emptySlice);

        // when
        videoFeedService.getVideos("005930", date, pageable);

        // then
        verify(videoRepository).findSummarizedVideos(
            "005930", date.atStartOfDay(), date.plusDays(1).atStartOfDay(), pageable);
    }

    @Test
    @DisplayName("[영상 상세 조회 시 요약 전문/핵심포인트/고지문/태깅 종목을 모두 채운다]")
    void getVideoDetail_returnsFullDetail() {
        // given
        videoFeedService = newService();
        Video video = videoOf(1L, "영상1");
        given(videoRepository.findSummarizedVideoById(1L)).willReturn(Optional.of(video));
        given(summaryRepository.findByVideo(video)).willReturn(Optional.of(summaryOf(video, "요약1")));
        given(videoTickerRepository.findByVideo(video))
            .willReturn(List.of(VideoTicker.of(video, "005930", "삼성전자", "BULLISH", BigDecimal.valueOf(0.8))));

        // when
        VideoFeedDetailResponse result = videoFeedService.getVideoDetail(1L);

        // then
        assertThat(result.summary()).isEqualTo("요약1");
        assertThat(result.keyPoints()).containsExactly("포인트1", "포인트2");
        assertThat(result.caveat()).isEqualTo("투자 권유 아님");
        assertThat(result.tickers()).hasSize(1);
    }

    @Test
    @DisplayName("[존재하지 않거나 아직 요약되지 않은 영상이면 NotFoundException을 던진다]")
    void getVideoDetail_videoNotFoundOrNotSummarized_throwsNotFoundException() {
        // given
        videoFeedService = newService();
        given(videoRepository.findSummarizedVideoById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> videoFeedService.getVideoDetail(999L))
            .isInstanceOf(NotFoundException.class);
    }
}
