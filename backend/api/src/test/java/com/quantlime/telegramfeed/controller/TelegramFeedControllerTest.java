package com.quantlime.telegramfeed.controller;

import com.quantlime.support.ApiTestSupport;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostTicker;
import com.quantlime.telegramfeed.domain.TelegramSummary;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.telegramfeed.repository.TelegramPostTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramSummaryRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class TelegramFeedControllerTest extends ApiTestSupport {

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private TelegramPostRepository telegramPostRepository;

    @Autowired
    private TelegramSummaryRepository telegramSummaryRepository;

    @Autowired
    private TelegramPostTickerRepository telegramPostTickerRepository;

    // channel.uk_channel(platform, external_channel_id) 유니크 제약 때문에 같은
    // handle로 여러 글을 시딩하는 테스트에서는 채널을 매번 새로 만들지 않고
    // 재사용해야 한다(JUnit 기본 PER_METHOD 라이프사이클이라 테스트 메서드마다
    // 새 인스턴스가 만들어져 이 캐시도 자동으로 초기화됨).
    private final Map<String, Channel> channelCache = new HashMap<>();

    private TelegramPost seedSummarizedPost(String handle, long messageId, String content, String tickerCode) {
        return seedSummarizedPost(handle, messageId, content, tickerCode, LocalDateTime.now());
    }

    private TelegramPost seedSummarizedPost(
        String handle, long messageId, String content, String tickerCode, LocalDateTime publishedAt) {
        Channel channel = channelCache.computeIfAbsent(handle, h -> {
            Channel newChannel = Channel.ofTelegram(h, "테스트 채널", 30,
                new TelegramFilterConfig(300, 2, List.of(), List.of()));
            newChannel.updateProfileImageUrl("https://cdn.example.com/" + h + ".jpg");
            return channelRepository.save(newChannel);
        });
        return seedSummarizedPost(handle, messageId, content, tickerCode, publishedAt, channel);
    }

    private TelegramPost seedSummarizedPost(
        String handle, long messageId, String content, String tickerCode,
        LocalDateTime publishedAt, Channel channel) {
        channel.updateProfileImageUrl("https://cdn.example.com/" + handle + ".jpg");
        channel = channelRepository.save(channel);
        TelegramPost post = telegramPostRepository.save(TelegramPost.of(
            channel, handle + "/" + messageId, messageId, content, publishedAt, 100L, LocalDateTime.now(), false));
        post.markSelected();
        post.markSummarized();
        post = telegramPostRepository.save(post);
        telegramSummaryRepository.save(TelegramSummary.of(post, "gemini-3.5-flash-lite",
            "{\"summary\":\"요약 내용\",\"key_points\":[\"포인트1\"],"
                + "\"mentioned_tickers\":[],\"caveat\":\"투자 권유 아님\"}",
            100, 50));
        if (tickerCode != null) {
            telegramPostTickerRepository.save(
                TelegramPostTicker.of(post, tickerCode, "애플", "BULLISH", BigDecimal.valueOf(0.8)));
        }
        return post;
    }

    @Test
    @DisplayName("[요약된 텔레그램 글 목록은 로그인 없이도 조회 가능하고 최신순으로 반환하며 채널 아바타/링크를 포함한다]")
    void getPosts_withoutAuth_returnsSummarizedPostsNewestFirst() throws Exception {
        // given
        seedSummarizedPost("insidertracking", 1L, "오래된 글",
            null, LocalDateTime.now().minusDays(1));
        seedSummarizedPost("insidertracking", 2L, "최신 글", null);

        // when & then
        mockMvc.perform(get("/api/telegram-feed/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].summary").value("요약 내용"))
            .andExpect(jsonPath("$.content[0].postUrl").value("https://t.me/insidertracking/2"))
            .andExpect(jsonPath("$.content[0].channelUrl").value("https://t.me/insidertracking"))
            .andExpect(jsonPath("$.content[0].channelProfileImageUrl")
                .value("https://cdn.example.com/insidertracking.jpg"));
    }

    @Test
    @DisplayName("[tickerCode로 필터링하면 해당 종목이 태깅된 글만 반환한다]")
    void getPosts_withTickerCode_returnsOnlyTaggedPosts() throws Exception {
        // given
        seedSummarizedPost("insidertracking", 1L, "애플 관련 글", "AAPL");
        seedSummarizedPost("insidertracking", 2L, "무관한 글", null);

        // when & then
        mockMvc.perform(get("/api/telegram-feed/posts").param("tickerCode", "AAPL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].tickers[0].tickerCode").value("AAPL"));
    }

    @Test
    @DisplayName("[date로 필터링하면 해당 날짜에 게시된 글만 반환한다]")
    void getPosts_withDate_returnsOnlyPostsPublishedOnThatDay() throws Exception {
        // given
        seedSummarizedPost("insidertracking", 1L, "당일 글", null, LocalDateTime.of(2026, 8, 14, 15, 0));
        seedSummarizedPost("insidertracking", 2L, "다른날 글", null, LocalDateTime.of(2026, 8, 13, 15, 0));

        // when & then
        mockMvc.perform(get("/api/telegram-feed/posts").param("date", "2026-08-14"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].summary").value("요약 내용"));
    }

    @Test
    @DisplayName("[channelId로 필터링하면 해당 채널 글만 반환한다]")
    void getPosts_withChannelId_returnsOnlyThatChannelPosts() throws Exception {
        // given
        Channel channelA = Channel.ofTelegram("channelA", "채널A", 30, new TelegramFilterConfig(300, 2, List.of(), List.of()));
        Channel channelB = Channel.ofTelegram("channelB", "채널B", 30, new TelegramFilterConfig(300, 2, List.of(), List.of()));
        seedSummarizedPost("channelA", 1L, "채널A 글", null, LocalDateTime.now(), channelA);
        TelegramPost postB = seedSummarizedPost("channelB", 1L, "채널B 글", null, LocalDateTime.now(), channelB);

        // when & then
        mockMvc.perform(get("/api/telegram-feed/posts")
                .param("channelId", postB.getChannel().getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].channelUrl").value("https://t.me/channelB"));
    }

    @Test
    @DisplayName("[채널 필터 목록은 로그인 없이도 조회 가능하고 활성 채널을 우선순위순으로 반환한다]")
    void getChannels_withoutAuth_returnsEnabledChannelsOrderedByPriority() throws Exception {
        // given
        Channel highPriority = channelRepository.save(Channel.ofTelegram(
            "high", "우선순위높음", 10, new TelegramFilterConfig(300, 2, List.of(), List.of())));
        Channel lowPriority = channelRepository.save(Channel.ofTelegram(
            "low", "우선순위낮음", 20, new TelegramFilterConfig(300, 2, List.of(), List.of())));

        // when & then
        mockMvc.perform(get("/api/telegram-feed/channels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].channelId").value(highPriority.getId()))
            .andExpect(jsonPath("$[0].name").value("우선순위높음"))
            .andExpect(jsonPath("$[1].channelId").value(lowPriority.getId()))
            .andExpect(jsonPath("$[1].name").value("우선순위낮음"));
    }

    @Test
    @DisplayName("[글 상세 조회는 원문 전문/요약 전문/핵심포인트/태깅 종목을 모두 반환한다]")
    void getPostDetail_returnsFullDetail() throws Exception {
        // given
        TelegramPost post = seedSummarizedPost("insidertracking", 1L, "본문 전문 내용", "AAPL");

        // when & then
        mockMvc.perform(get("/api/telegram-feed/posts/{telegramPostId}", post.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("본문 전문 내용"))
            .andExpect(jsonPath("$.summary").value("요약 내용"))
            .andExpect(jsonPath("$.keyPoints[0]").value("포인트1"))
            .andExpect(jsonPath("$.caveat").value("투자 권유 아님"))
            .andExpect(jsonPath("$.tickers[0].tickerCode").value("AAPL"));
    }

    @Test
    @DisplayName("[존재하지 않는 글을 조회하면 404를 반환한다]")
    void getPostDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/telegram-feed/posts/{telegramPostId}", 999_999L))
            .andExpect(status().isNotFound());
    }
}
