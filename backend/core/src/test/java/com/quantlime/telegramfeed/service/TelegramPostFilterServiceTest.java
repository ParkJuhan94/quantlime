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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        Channel channel = channelOf(new TelegramFilterConfig(50, 5, List.of(), List.of()));
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
        Channel channel = channelOf(new TelegramFilterConfig(300, 5, List.of(), List.of()));
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
        Channel channel = channelOf(new TelegramFilterConfig(10, 5, List.of("광고"), List.of()));
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
        Channel channel = channelOf(new TelegramFilterConfig(5, 5, List.of(), List.of("엔비디아")));
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
        Channel channel = channelOf(new TelegramFilterConfig(5, 5, List.of(), List.of()));
        TelegramPost post = postOf(channel, "충분히 긴 정상 게시물입니다", LocalDateTime.now());
        given(telegramPostRepository.findByChannelAndStatus(channel, TelegramPostStatus.DISCOVERED))
            .willReturn(List.of(post));

        // when
        telegramPostFilterService.applyFilters(channel);

        // then
        assertThat(post.getStatus()).isEqualTo(TelegramPostStatus.SELECTED);
    }

    @Test
    @DisplayName("[선정 후보가 max_per_run을 넘으면 글자 수가 짧은 글부터 FILTERED_OUT 처리한다(속보보다 분석글 우선)]")
    void applyFilters_exceedsMaxPerRun_filtersOutShorterPost() {
        // given
        Channel channel = channelOf(new TelegramFilterConfig(5, 1, List.of(), List.of()));
        TelegramPost longPost = postOf(channel, "이것은 아주 길고 상세한 분석 게시물입니다 훨씬 더 깁니다", LocalDateTime.now());
        TelegramPost shortPost = postOf(channel, "짧은 편인 게시물", LocalDateTime.now());
        given(telegramPostRepository.findByChannelAndStatus(channel, TelegramPostStatus.DISCOVERED))
            .willReturn(List.of(shortPost, longPost));

        // when
        telegramPostFilterService.applyFilters(channel);

        // then
        assertThat(longPost.getStatus()).isEqualTo(TelegramPostStatus.SELECTED);
        assertThat(shortPost.getStatus()).isEqualTo(TelegramPostStatus.FILTERED_OUT);
    }

    @Test
    @DisplayName("[max_per_run은 수집 사이클이 아니라 발행일 기준 하루 단위로 적용된다]")
    void applyFilters_maxPerRunAppliesPerPublishedDateNotPerRun() {
        // given: 서로 다른 이틀에 발행된 글이 한 사이클에 함께 후보로 잡힌 상황.
        // max_per_run=1이라도 날짜가 다르면 각 날짜에서 독립적으로 1개씩,
        // 총 2개가 선정돼야 한다.
        Channel channel = channelOf(new TelegramFilterConfig(5, 1, List.of(), List.of()));
        TelegramPost today = postOf(channel, "오늘 게시물입니다", LocalDateTime.now());
        TelegramPost yesterday = postOf(channel, "어제 게시물입니다", LocalDateTime.now().minusDays(1));
        given(telegramPostRepository.findByChannelAndStatus(channel, TelegramPostStatus.DISCOVERED))
            .willReturn(List.of(today, yesterday));

        // when
        telegramPostFilterService.applyFilters(channel);

        // then
        assertThat(today.getStatus()).isEqualTo(TelegramPostStatus.SELECTED);
        assertThat(yesterday.getStatus()).isEqualTo(TelegramPostStatus.SELECTED);
    }

    @Test
    @DisplayName("[해당 날짜에 이미 max_per_run만큼 선택돼 있으면 남은 쿼터가 0이라 신규 후보는 전부 컷된다]")
    void applyFilters_dailyQuotaAlreadyExhausted_filtersOutAllNewCandidates() {
        // given
        Channel channel = channelOf(new TelegramFilterConfig(5, 1, List.of(), List.of()));
        TelegramPost post = postOf(channel, "이번 사이클 신규 게시물입니다", LocalDateTime.now());
        given(telegramPostRepository.findByChannelAndStatus(channel, TelegramPostStatus.DISCOVERED))
            .willReturn(List.of(post));
        given(telegramPostRepository.countByChannelAndStatusAndPublishedAtBetween(
            eq(channel), eq(TelegramPostStatus.SELECTED), any(), any()))
            .willReturn(1);

        // when
        telegramPostFilterService.applyFilters(channel);

        // then
        assertThat(post.getStatus()).isEqualTo(TelegramPostStatus.FILTERED_OUT);
    }

    private Channel channelOf(TelegramFilterConfig filterConfig) {
        return Channel.ofTelegram("testhandle", "테스트 채널", 30, filterConfig);
    }

    private TelegramPost postOf(Channel channel, String content, LocalDateTime publishedAt) {
        return TelegramPost.of(channel, "testhandle/" + content.hashCode(), content.hashCode(),
            content, publishedAt, 100L, LocalDateTime.now(), false);
    }
}
