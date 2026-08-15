package com.quantlime.telegramfeed.service;

import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
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
class TelegramPostFilterServiceTest {

    @Mock
    private TelegramPostRepository telegramPostRepository;

    @InjectMocks
    private TelegramPostFilterService telegramPostFilterService;

    @Test
    @DisplayName("[보존 기간(14일)을 이미 넘긴 글은 다른 필터보다 먼저 FILTERED_OUT 처리한다]")
    void applyFilters_alreadyPastRetentionWindow_filtersOutBeforeOtherChecks() {
        // given
        Channel channel = channelOf(new TelegramFilterConfig(50, List.of(), List.of()));
        TelegramPost post = postOf(channel, "충분히 긴 본문이지만 보존기간을 넘긴 글입니다".repeat(5),
            LocalDateTime.now().minusDays(15));
        given(telegramPostRepository.findByChannelAndStatus(channel, TelegramPostStatus.DISCOVERED))
            .willReturn(List.of(post));

        // when
        telegramPostFilterService.applyFilters(channel);

        // then
        assertThat(post.getStatus()).isEqualTo(TelegramPostStatus.FILTERED_OUT);
    }

    @Test
    @DisplayName("[본문 글자 수가 min_char_count 미만이면 FILTERED_OUT 처리한다]")
    void applyFilters_belowMinCharCount_filtersOut() {
        // given
        Channel channel = channelOf(new TelegramFilterConfig(300, List.of(), List.of()));
        TelegramPost post = postOf(channel, "짧은 속보", LocalDateTime.now());
        given(telegramPostRepository.findByChannelAndStatus(channel, TelegramPostStatus.DISCOVERED))
            .willReturn(List.of(post));

        // when
        telegramPostFilterService.applyFilters(channel);

        // then
        assertThat(post.getStatus()).isEqualTo(TelegramPostStatus.FILTERED_OUT);
    }

    @Test
    @DisplayName("[제외 키워드가 본문에 포함되면 FILTERED_OUT 처리한다]")
    void applyFilters_contentExcludeMatch_filtersOut() {
        // given
        Channel channel = channelOf(new TelegramFilterConfig(10, List.of("광고"), List.of()));
        TelegramPost post = postOf(channel, "이것은 광고성 게시물입니다 자세히 보세요", LocalDateTime.now());
        given(telegramPostRepository.findByChannelAndStatus(channel, TelegramPostStatus.DISCOVERED))
            .willReturn(List.of(post));

        // when
        telegramPostFilterService.applyFilters(channel);

        // then
        assertThat(post.getStatus()).isEqualTo(TelegramPostStatus.FILTERED_OUT);
    }

    @Test
    @DisplayName("[포함 키워드 목록이 있는데 본문에 하나도 안 걸리면 FILTERED_OUT 처리한다]")
    void applyFilters_contentIncludeMissing_filtersOut() {
        // given
        Channel channel = channelOf(new TelegramFilterConfig(5, List.of(), List.of("엔비디아")));
        TelegramPost post = postOf(channel, "테슬라 주가가 급등했습니다", LocalDateTime.now());
        given(telegramPostRepository.findByChannelAndStatus(channel, TelegramPostStatus.DISCOVERED))
            .willReturn(List.of(post));

        // when
        telegramPostFilterService.applyFilters(channel);

        // then
        assertThat(post.getStatus()).isEqualTo(TelegramPostStatus.FILTERED_OUT);
    }

    @Test
    @DisplayName("[하드필터를 전부 통과하면 SELECTED 처리한다]")
    void applyFilters_passesAllHardFilters_selects() {
        // given
        Channel channel = channelOf(new TelegramFilterConfig(5, List.of(), List.of()));
        TelegramPost post = postOf(channel, "충분히 긴 정상 게시물입니다", LocalDateTime.now());
        given(telegramPostRepository.findByChannelAndStatus(channel, TelegramPostStatus.DISCOVERED))
            .willReturn(List.of(post));

        // when
        telegramPostFilterService.applyFilters(channel);

        // then
        assertThat(post.getStatus()).isEqualTo(TelegramPostStatus.SELECTED);
    }

    @Test
    @DisplayName("[하드필터를 통과한 글은 하루에 몇 건이든 개수 제한 없이 전부 SELECTED 처리한다(다이제스트 재료이므로 상한 없음)]")
    void applyFilters_multiplePassingPosts_selectsAllWithoutDailyCap() {
        // given
        Channel channel = channelOf(new TelegramFilterConfig(5, List.of(), List.of()));
        TelegramPost post1 = postOf(channel, "이것은 아주 길고 상세한 분석 게시물입니다 훨씬 더 깁니다", LocalDateTime.now());
        TelegramPost post2 = postOf(channel, "짧은 편인 게시물", LocalDateTime.now());
        TelegramPost post3 = postOf(channel, "세 번째 게시물입니다", LocalDateTime.now());
        given(telegramPostRepository.findByChannelAndStatus(channel, TelegramPostStatus.DISCOVERED))
            .willReturn(List.of(post1, post2, post3));

        // when
        telegramPostFilterService.applyFilters(channel);

        // then
        assertThat(post1.getStatus()).isEqualTo(TelegramPostStatus.SELECTED);
        assertThat(post2.getStatus()).isEqualTo(TelegramPostStatus.SELECTED);
        assertThat(post3.getStatus()).isEqualTo(TelegramPostStatus.SELECTED);
    }

    private Channel channelOf(TelegramFilterConfig filterConfig) {
        return Channel.ofTelegram("testhandle", "테스트 채널", 30, filterConfig);
    }

    private TelegramPost postOf(Channel channel, String content, LocalDateTime publishedAt) {
        return TelegramPost.of(channel, "testhandle/" + content.hashCode(), content.hashCode(),
            content, publishedAt, 100L, LocalDateTime.now(), false);
    }
}
