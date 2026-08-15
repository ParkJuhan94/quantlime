package com.quantlime.telegramfeed.controller;

import com.quantlime.support.ApiTestSupport;
import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.telegramfeed.domain.TelegramDigestTicker;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.repository.TelegramDigestRepository;
import com.quantlime.telegramfeed.repository.TelegramDigestTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    private TelegramDigestRepository telegramDigestRepository;

    @Autowired
    private TelegramDigestTickerRepository telegramDigestTickerRepository;

    // channel.uk_channel(platform, external_channel_id) 유니크 제약 때문에 같은
    // handle로 여러 다이제스트를 시딩하는 테스트에서는 채널을 매번 새로 만들지
    // 않고 재사용해야 한다.
    private final Map<String, Channel> channelCache = new HashMap<>();

    private TelegramDigest seedDigest(String handle, LocalDate digestDate, String tickerCode) {
        Channel channel = channelCache.computeIfAbsent(handle, h -> {
            Channel newChannel = Channel.ofTelegram(h, "테스트 채널", 30,
                new TelegramFilterConfig(300, List.of(), List.of()));
            newChannel.updateProfileImageUrl("https://cdn.example.com/" + h + ".jpg");
            return channelRepository.save(newChannel);
        });
        return seedDigest(handle, digestDate, tickerCode, channel);
    }

    private TelegramDigest seedDigest(String handle, LocalDate digestDate, String tickerCode, Channel channel) {
        TelegramPost post = telegramPostRepository.save(TelegramPost.of(channel,
            handle + "/" + digestDate.hashCode(), digestDate.hashCode(), "본문 전문 내용",
            digestDate.atTime(9, 0), 100L, LocalDateTime.now(), false));
        post.markSelected();
        telegramPostRepository.save(post);

        TelegramDigest digest = telegramDigestRepository.save(TelegramDigest.of(channel, digestDate,
            "gemini-3.5-flash-lite",
            "{\"summary\":\"요약 내용\",\"key_points\":[\"포인트1\"],"
                + "\"mentioned_tickers\":[],\"caveat\":\"투자 권유 아님\"}",
            100, 50));
        if (tickerCode != null) {
            telegramDigestTickerRepository.save(
                TelegramDigestTicker.of(digest, tickerCode, "애플", "BULLISH", BigDecimal.valueOf(0.8)));
        }
        return digest;
    }

    @Test
    @DisplayName("[다이제스트 목록은 로그인 없이도 조회 가능하고 최신순으로 반환하며 채널 아바타/링크를 포함한다]")
    void getDigests_withoutAuth_returnsDigestsNewestFirst() throws Exception {
        // given
        seedDigest("insidertracking", LocalDate.now().minusDays(1), null);
        seedDigest("insidertracking", LocalDate.now(), null);

        // when & then
        mockMvc.perform(get("/api/telegram-feed/digests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].summary").value("요약 내용"))
            .andExpect(jsonPath("$.content[0].sourcePostCount").value(1))
            .andExpect(jsonPath("$.content[0].channelUrl").value("https://t.me/insidertracking"))
            .andExpect(jsonPath("$.content[0].channelProfileImageUrl")
                .value("https://cdn.example.com/insidertracking.jpg"));
    }

    @Test
    @DisplayName("[tickerCode로 필터링하면 해당 종목이 태깅된 다이제스트만 반환한다]")
    void getDigests_withTickerCode_returnsOnlyTaggedDigests() throws Exception {
        // given
        seedDigest("insidertracking", LocalDate.now(), "AAPL");
        seedDigest("insidertracking", LocalDate.now().minusDays(1), null);

        // when & then
        mockMvc.perform(get("/api/telegram-feed/digests").param("tickerCode", "AAPL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].tickers[0].tickerCode").value("AAPL"));
    }

    @Test
    @DisplayName("[date로 필터링하면 해당 날짜 다이제스트만 반환한다]")
    void getDigests_withDate_returnsOnlyThatDateDigest() throws Exception {
        // given
        seedDigest("insidertracking", LocalDate.of(2026, 8, 14), null);
        seedDigest("insidertracking", LocalDate.of(2026, 8, 13), null);

        // when & then
        mockMvc.perform(get("/api/telegram-feed/digests").param("date", "2026-08-14"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].summary").value("요약 내용"));
    }

    @Test
    @DisplayName("[channelId로 필터링하면 해당 채널 다이제스트만 반환한다]")
    void getDigests_withChannelId_returnsOnlyThatChannelDigests() throws Exception {
        // given
        Channel channelA = Channel.ofTelegram("channelA", "채널A", 30, new TelegramFilterConfig(300, List.of(), List.of()));
        Channel channelB = Channel.ofTelegram("channelB", "채널B", 30, new TelegramFilterConfig(300, List.of(), List.of()));
        seedDigest("channelA", LocalDate.now(), null, channelRepository.save(channelA));
        TelegramDigest digestB = seedDigest("channelB", LocalDate.now(), null, channelRepository.save(channelB));

        // when & then
        mockMvc.perform(get("/api/telegram-feed/digests")
                .param("channelId", digestB.getChannel().getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].channelUrl").value("https://t.me/channelB"));
    }

    @Test
    @DisplayName("[채널 필터 목록은 로그인 없이도 조회 가능하고 활성 채널을 우선순위순으로 반환한다]")
    void getChannels_withoutAuth_returnsEnabledChannelsOrderedByPriority() throws Exception {
        // given
        Channel highPriority = channelRepository.save(Channel.ofTelegram(
            "high", "우선순위높음", 10, new TelegramFilterConfig(300, List.of(), List.of())));
        Channel lowPriority = channelRepository.save(Channel.ofTelegram(
            "low", "우선순위낮음", 20, new TelegramFilterConfig(300, List.of(), List.of())));

        // when & then
        mockMvc.perform(get("/api/telegram-feed/channels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].channelId").value(highPriority.getId()))
            .andExpect(jsonPath("$[0].name").value("우선순위높음"))
            .andExpect(jsonPath("$[1].channelId").value(lowPriority.getId()))
            .andExpect(jsonPath("$[1].name").value("우선순위낮음"));
    }

    @Test
    @DisplayName("[다이제스트 상세 조회는 요약 전문/핵심포인트/태깅 종목/원문 링크 목록을 모두 반환한다]")
    void getDigestDetail_returnsFullDetail() throws Exception {
        // given
        TelegramDigest digest = seedDigest("insidertracking", LocalDate.now(), "AAPL");

        // when & then
        mockMvc.perform(get("/api/telegram-feed/digests/{telegramDigestId}", digest.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary").value("요약 내용"))
            .andExpect(jsonPath("$.keyPoints[0]").value("포인트1"))
            .andExpect(jsonPath("$.caveat").value("투자 권유 아님"))
            .andExpect(jsonPath("$.tickers[0].tickerCode").value("AAPL"))
            .andExpect(jsonPath("$.sourcePostUrls.length()").value(1));
    }

    @Test
    @DisplayName("[존재하지 않는 다이제스트를 조회하면 404를 반환한다]")
    void getDigestDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/telegram-feed/digests/{telegramDigestId}", 999_999L))
            .andExpect(status().isNotFound());
    }
}
