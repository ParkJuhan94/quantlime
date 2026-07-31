package com.quantlime.videofeed.controller;

import com.quantlime.support.ApiTestSupport;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Summary;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoTicker;
import com.quantlime.videofeed.repository.ChannelRepository;
import com.quantlime.videofeed.repository.SummaryRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import com.quantlime.videofeed.repository.VideoTickerRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class VideoFeedControllerTest extends ApiTestSupport {

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private SummaryRepository summaryRepository;

    @Autowired
    private VideoTickerRepository videoTickerRepository;

    private Video seedSummarizedVideo(String externalVideoId, String title, String tickerCode) {
        return seedSummarizedVideo(externalVideoId, title, tickerCode, LocalDateTime.now());
    }

    private Video seedSummarizedVideo(
        String externalVideoId, String title, String tickerCode, LocalDateTime publishedAt) {
        Channel channel = Channel.of(Platform.YOUTUBE, "UC" + externalVideoId, "UU" + externalVideoId,
            "테스트 채널", 10, new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of()));
        channel.updateProfileImageUrl("https://yt3.example.com/UC" + externalVideoId + ".jpg");
        channel = channelRepository.save(channel);
        Video video = videoRepository.save(Video.of(
            channel, externalVideoId, title, publishedAt, 300, 100L, LocalDateTime.now()));
        video.markSelected();
        video.markTranscribed();
        video.markSummarized();
        video = videoRepository.save(video);
        summaryRepository.save(Summary.of(video, "gemini-3.5-flash-lite",
            "{\"summary\":\"요약 내용\",\"key_points\":[\"포인트1\"],"
                + "\"mentioned_tickers\":[],\"caveat\":\"투자 권유 아님\"}",
            100, 50));
        if (tickerCode != null) {
            videoTickerRepository.save(VideoTicker.of(video, tickerCode, "삼성전자", "BULLISH", BigDecimal.valueOf(0.8)));
        }
        return video;
    }

    @Test
    @DisplayName("[요약된 영상 목록은 로그인 없이도 조회 가능하고 최신순으로 반환하며 채널 아바타/링크를 포함한다]")
    void getVideos_withoutAuth_returnsSummarizedVideosNewestFirst() throws Exception {
        // given
        seedSummarizedVideo("vid-old", "오래된 영상", null);
        seedSummarizedVideo("vid-new", "최신 영상", null);

        // when & then
        mockMvc.perform(get("/api/video-feed/videos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("최신 영상"))
            .andExpect(jsonPath("$.content[0].summary").value("요약 내용"))
            .andExpect(jsonPath("$.content[0].videoUrl").value("https://www.youtube.com/watch?v=vid-new"))
            .andExpect(jsonPath("$.content[0].channelUrl").value("https://www.youtube.com/channel/UCvid-new"))
            .andExpect(jsonPath("$.content[0].channelProfileImageUrl").value("https://yt3.example.com/UCvid-new.jpg"));
    }

    @Test
    @DisplayName("[tickerCode로 필터링하면 해당 종목이 태깅된 영상만 반환한다]")
    void getVideos_withTickerCode_returnsOnlyTaggedVideos() throws Exception {
        // given
        seedSummarizedVideo("vid-tagged", "삼성전자 영상", "005930");
        seedSummarizedVideo("vid-untagged", "무관한 영상", null);

        // when & then
        mockMvc.perform(get("/api/video-feed/videos").param("tickerCode", "005930"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("삼성전자 영상"));
    }

    @Test
    @DisplayName("[date로 필터링하면 해당 날짜에 게시된 영상만 반환한다]")
    void getVideos_withDate_returnsOnlyVideosPublishedOnThatDay() throws Exception {
        // given
        seedSummarizedVideo("vid-target-day", "당일 영상", null, LocalDateTime.of(2026, 7, 30, 15, 0));
        seedSummarizedVideo("vid-other-day", "다른날 영상", null, LocalDateTime.of(2026, 7, 29, 15, 0));
        seedSummarizedVideo("vid-boundary", "자정 경계 영상", null, LocalDateTime.of(2026, 7, 31, 0, 0));

        // when & then
        mockMvc.perform(get("/api/video-feed/videos").param("date", "2026-07-30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("당일 영상"));
    }

    @Test
    @DisplayName("[영상 상세 조회는 요약 전문/핵심포인트/태깅 종목을 모두 반환한다]")
    void getVideoDetail_returnsFullDetail() throws Exception {
        // given
        Video video = seedSummarizedVideo("vid-detail", "상세 영상", "005930");

        // when & then
        mockMvc.perform(get("/api/video-feed/videos/{videoId}", video.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary").value("요약 내용"))
            .andExpect(jsonPath("$.keyPoints[0]").value("포인트1"))
            .andExpect(jsonPath("$.caveat").value("투자 권유 아님"))
            .andExpect(jsonPath("$.tickers[0].tickerCode").value("005930"));
    }

    @Test
    @DisplayName("[존재하지 않는 영상을 조회하면 404를 반환한다]")
    void getVideoDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/video-feed/videos/{videoId}", 999_999L))
            .andExpect(status().isNotFound());
    }
}
