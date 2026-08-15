package com.quantlime.telegramfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostTicker;
import com.quantlime.telegramfeed.domain.TelegramSummary;
import com.quantlime.telegramfeed.dto.response.TelegramFeedChannelResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDetailResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedPostResponse;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.telegramfeed.repository.TelegramPostTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramSummaryRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import com.quantlime.videofeed.repository.ChannelRepository;
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
class TelegramFeedServiceTest {

    @Mock
    private TelegramPostRepository telegramPostRepository;

    @Mock
    private TelegramSummaryRepository telegramSummaryRepository;

    @Mock
    private TelegramPostTickerRepository telegramPostTickerRepository;

    @Mock
    private ChannelRepository channelRepository;

    private TelegramFeedService telegramFeedService;

    private Channel channelOf() {
        return Channel.ofTelegram("insidertracking", "테스트 채널", 30,
            new TelegramFilterConfig(300, 2, List.of(), List.of()));
    }

    private TelegramPost postOf(Long id, String content) {
        Channel channel = channelOf();
        TelegramPost post = TelegramPost.of(channel, "insidertracking/" + id, id, content,
            LocalDateTime.now(), 100L, LocalDateTime.now(), false);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private TelegramSummary summaryOf(TelegramPost post, String summaryText) {
        String payload = "{\"summary\":\"" + summaryText
            + "\",\"key_points\":[\"포인트1\",\"포인트2\"],"
            + "\"macro_points\":[\"매크로포인트1\"],"
            + "\"mentioned_tickers\":[],\"caveat\":\"투자 권유 아님\"}";
        TelegramSummary summary = TelegramSummary.of(post, "gemini-3.5-flash-lite", payload, 100, 50);
        ReflectionTestUtils.setField(summary, "id", post.getId());
        return summary;
    }

    private TelegramSummary summaryOfWithoutMacroPoints(TelegramPost post, String summaryText) {
        String payload = "{\"summary\":\"" + summaryText
            + "\",\"key_points\":[\"포인트1\"],"
            + "\"mentioned_tickers\":[],\"caveat\":\"투자 권유 아님\"}";
        TelegramSummary summary = TelegramSummary.of(post, "gemini-3.5-flash-lite", payload, 100, 50);
        ReflectionTestUtils.setField(summary, "id", post.getId());
        return summary;
    }

    private TelegramFeedService newService() {
        return new TelegramFeedService(telegramPostRepository, telegramSummaryRepository,
            telegramPostTickerRepository, channelRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("[tickerCode/date가 없으면 전체 요약 글을 최신순으로 조회하고, 각 글의 요약문/태깅 종목을 배치로 채운다]")
    void getPosts_withoutFilters_returnsAllSummarizedPosts() {
        // given
        telegramFeedService = newService();
        TelegramPost post1 = postOf(1L, "본문1");
        TelegramPost post2 = postOf(2L, "본문2");
        Pageable pageable = PageRequest.of(0, 10);
        given(telegramPostRepository.findSummarizedPosts(null, null, null, null, pageable))
            .willReturn(new SliceImpl<>(List.of(post1, post2)));
        given(telegramSummaryRepository.findByTelegramPost_IdIn(List.of(1L, 2L)))
            .willReturn(List.of(summaryOf(post1, "요약1"), summaryOf(post2, "요약2")));
        given(telegramPostTickerRepository.findByTelegramPost_IdIn(List.of(1L, 2L)))
            .willReturn(List.of(TelegramPostTicker.of(post1, "AAPL", "애플", "BULLISH", BigDecimal.valueOf(0.8))));

        // when
        Slice<TelegramFeedPostResponse> result = telegramFeedService.getPosts(null, null, null, pageable);

        // then
        assertThat(result.getContent()).hasSize(2);
        TelegramFeedPostResponse item1 = result.getContent().get(0);
        assertThat(item1.summary()).isEqualTo("요약1");
        assertThat(item1.tickers()).hasSize(1);
        assertThat(item1.tickers().get(0).tickerCode()).isEqualTo("AAPL");
        assertThat(item1.postUrl()).isEqualTo("https://t.me/insidertracking/1");
        assertThat(item1.channelUrl()).isEqualTo("https://t.me/insidertracking");

        TelegramFeedPostResponse item2 = result.getContent().get(1);
        assertThat(item2.summary()).isEqualTo("요약2");
        assertThat(item2.tickers()).isEmpty();
    }

    @Test
    @DisplayName("[tickerCode/channelId/date를 지정하면 하루 범위(00:00~다음날 00:00)와 함께 리포지토리에 그대로 넘긴다]")
    void getPosts_withTickerCodeChannelIdAndDate_passesFiltersToRepository() {
        // given
        telegramFeedService = newService();
        Pageable pageable = PageRequest.of(0, 10);
        Slice<TelegramPost> emptySlice = new SliceImpl<>(List.of());
        LocalDate date = LocalDate.of(2026, 8, 14);
        given(telegramPostRepository.findSummarizedPosts(
            eq("AAPL"), eq(1L), eq(date.atStartOfDay()), eq(date.plusDays(1).atStartOfDay()), eq(pageable)))
            .willReturn(emptySlice);

        // when
        telegramFeedService.getPosts("AAPL", 1L, date, pageable);

        // then
        verify(telegramPostRepository).findSummarizedPosts(
            "AAPL", 1L, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), pageable);
    }

    @Test
    @DisplayName("[채널 필터 목록은 Platform.TELEGRAM 활성 채널만 우선순위순으로 반환한다]")
    void getChannels_returnsEnabledTelegramChannelsOrderedByPriority() {
        // given
        telegramFeedService = newService();
        Channel channel = channelOf();
        ReflectionTestUtils.setField(channel, "id", 1L);
        given(channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.TELEGRAM))
            .willReturn(List.of(channel));

        // when
        List<TelegramFeedChannelResponse> result = telegramFeedService.getChannels();

        // then
        assertThat(result).containsExactly(new TelegramFeedChannelResponse(1L, "테스트 채널"));
    }

    @Test
    @DisplayName("[글 상세 조회 시 원문 전문/요약 전문/핵심포인트/고지문/태깅 종목을 모두 채운다]")
    void getPostDetail_returnsFullDetail() {
        // given
        telegramFeedService = newService();
        TelegramPost post = postOf(1L, "본문 전문 내용");
        given(telegramPostRepository.findSummarizedPostById(1L)).willReturn(Optional.of(post));
        given(telegramSummaryRepository.findByTelegramPost(post)).willReturn(Optional.of(summaryOf(post, "요약1")));
        given(telegramPostTickerRepository.findByTelegramPost(post))
            .willReturn(List.of(TelegramPostTicker.of(post, "AAPL", "애플", "BULLISH", BigDecimal.valueOf(0.8))));

        // when
        TelegramFeedDetailResponse result = telegramFeedService.getPostDetail(1L);

        // then
        assertThat(result.content()).isEqualTo("본문 전문 내용");
        assertThat(result.summary()).isEqualTo("요약1");
        assertThat(result.keyPoints()).containsExactly("포인트1", "포인트2");
        assertThat(result.macroPoints()).containsExactly("매크로포인트1");
        assertThat(result.caveat()).isEqualTo("투자 권유 아님");
        assertThat(result.tickers()).hasSize(1);
    }

    @Test
    @DisplayName("[macro_points 필드가 없는 과거 요약 데이터를 조회해도 빈 리스트로 방어한다]")
    void getPostDetail_payloadWithoutMacroPoints_defaultsToEmptyList() {
        // given
        telegramFeedService = newService();
        TelegramPost post = postOf(1L, "본문");
        given(telegramPostRepository.findSummarizedPostById(1L)).willReturn(Optional.of(post));
        given(telegramSummaryRepository.findByTelegramPost(post))
            .willReturn(Optional.of(summaryOfWithoutMacroPoints(post, "요약1")));
        given(telegramPostTickerRepository.findByTelegramPost(post)).willReturn(List.of());

        // when
        TelegramFeedDetailResponse result = telegramFeedService.getPostDetail(1L);

        // then
        assertThat(result.macroPoints()).isEmpty();
    }

    @Test
    @DisplayName("[존재하지 않거나 아직 요약되지 않은 글이면 NotFoundException을 던진다]")
    void getPostDetail_notFoundOrNotSummarized_throwsNotFoundException() {
        // given
        telegramFeedService = newService();
        given(telegramPostRepository.findSummarizedPostById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> telegramFeedService.getPostDetail(999L))
            .isInstanceOf(NotFoundException.class);
    }
}
