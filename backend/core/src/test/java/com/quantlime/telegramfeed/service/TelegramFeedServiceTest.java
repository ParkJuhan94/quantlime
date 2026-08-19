package com.quantlime.telegramfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.telegramfeed.domain.TelegramDigestTicker;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.telegramfeed.dto.response.TelegramFeedChannelResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDigestDetailResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDigestResponse;
import com.quantlime.telegramfeed.repository.TelegramDigestRepository;
import com.quantlime.telegramfeed.repository.TelegramDigestTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramFeedServiceTest {

    @Mock
    private TelegramDigestRepository telegramDigestRepository;

    @Mock
    private TelegramDigestTickerRepository telegramDigestTickerRepository;

    @Mock
    private TelegramPostRepository telegramPostRepository;

    @Mock
    private ChannelRepository channelRepository;

    private TelegramFeedService telegramFeedService;

    private Channel channelOf() {
        Channel channel = Channel.ofTelegram("insidertracking", "테스트 채널", 30,
            new TelegramFilterConfig(300, List.of(), List.of()));
        // countSourcePostsByDigest가 channel.getId()로 집계 키를 만들기 때문에
        // (2026-08-19 N+1 제거 리팩터링) 테스트에서도 id가 필요하다 - 실제
        // 영속화된 엔티티와 동일하게 미리 세팅해둔다.
        ReflectionTestUtils.setField(channel, "id", 1L);
        return channel;
    }

    private TelegramDigest digestOf(Long id, Channel channel, LocalDate digestDate, String summaryText) {
        String payload = "{\"summary\":\"" + summaryText
            + "\",\"key_points\":[\"포인트1\",\"포인트2\"],"
            + "\"macro_points\":[\"매크로포인트1\"],"
            + "\"mentioned_tickers\":[],\"caveat\":\"투자 권유 아님\"}";
        TelegramDigest digest = TelegramDigest.of(channel, digestDate, "gemini-3.5-flash-lite", payload, 100, 50);
        ReflectionTestUtils.setField(digest, "id", id);
        return digest;
    }

    private TelegramDigest digestOfWithoutMacroPoints(Long id, Channel channel, LocalDate digestDate, String summaryText) {
        String payload = "{\"summary\":\"" + summaryText
            + "\",\"key_points\":[\"포인트1\"],"
            + "\"mentioned_tickers\":[],\"caveat\":\"투자 권유 아님\"}";
        TelegramDigest digest = TelegramDigest.of(channel, digestDate, "gemini-3.5-flash-lite", payload, 100, 50);
        ReflectionTestUtils.setField(digest, "id", id);
        return digest;
    }

    private TelegramFeedService newService() {
        return new TelegramFeedService(telegramDigestRepository, telegramDigestTickerRepository,
            telegramPostRepository, channelRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("[tickerCode/date가 없으면 전체 다이제스트를 최신순으로 조회하고, 각 다이제스트의 요약문/태깅 종목/재료 글 개수를 채운다]")
    void getDigests_withoutFilters_returnsAllDigests() {
        // given
        telegramFeedService = newService();
        Channel channel = channelOf();
        LocalDate date = LocalDate.of(2026, 8, 15);
        TelegramDigest digest1 = digestOf(1L, channel, date, "요약1");
        TelegramDigest digest2 = digestOf(2L, channel, date.minusDays(1), "요약2");
        Pageable pageable = PageRequest.of(0, 10);
        given(telegramDigestRepository.findDigests(null, null, null, pageable))
            .willReturn(new SliceImpl<>(List.of(digest1, digest2)));
        given(telegramDigestTickerRepository.findByTelegramDigest_IdIn(List.of(1L, 2L)))
            .willReturn(List.of(TelegramDigestTicker.of(digest1, "AAPL", "애플", "BULLISH", BigDecimal.valueOf(0.8))));
        // 다이제스트별 반복 조회(N+1) 대신 페이지 전체 날짜 범위를 한 번에
        // 집계하는 쿼리로 바뀌었다(2026-08-19) - digest1(date) 소속 글 1건만
        // 반환해, digest2(date-1일)는 소스 글 0건으로 집계되는지도 함께 검증한다.
        given(telegramPostRepository.findChannelIdAndPublishedAtForCounting(
            eq(List.of(1L)), eq(TelegramPostStatus.SELECTED), any(), any()))
            .willReturn(List.<Object[]>of(new Object[]{1L, date.atTime(9, 0)}));

        // when
        Slice<TelegramFeedDigestResponse> result = telegramFeedService.getDigests(null, null, null, pageable);

        // then
        assertThat(result.getContent()).hasSize(2);
        TelegramFeedDigestResponse item1 = result.getContent().get(0);
        assertThat(item1.summary()).isEqualTo("요약1");
        assertThat(item1.tickers()).hasSize(1);
        assertThat(item1.tickers().get(0).tickerCode()).isEqualTo("AAPL");
        assertThat(item1.sourcePostCount()).isEqualTo(1);
        assertThat(item1.channelUrl()).isEqualTo("https://t.me/insidertracking");

        TelegramFeedDigestResponse item2 = result.getContent().get(1);
        assertThat(item2.summary()).isEqualTo("요약2");
        assertThat(item2.tickers()).isEmpty();
        assertThat(item2.sourcePostCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("[tickerCode/channelId/date를 지정하면 리포지토리에 그대로 넘긴다]")
    void getDigests_withTickerCodeChannelIdAndDate_passesFiltersToRepository() {
        // given
        telegramFeedService = newService();
        Pageable pageable = PageRequest.of(0, 10);
        Slice<TelegramDigest> emptySlice = new SliceImpl<>(List.of());
        LocalDate date = LocalDate.of(2026, 8, 14);
        given(telegramDigestRepository.findDigests(eq("AAPL"), eq(1L), eq(date), eq(pageable)))
            .willReturn(emptySlice);

        // when
        telegramFeedService.getDigests("AAPL", 1L, date, pageable);

        // then
        verify(telegramDigestRepository).findDigests("AAPL", 1L, date, pageable);
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
    @DisplayName("[다이제스트 상세 조회 시 요약 전문/핵심포인트/고지문/태깅 종목/원문 링크 목록을 모두 채운다]")
    void getDigestDetail_returnsFullDetail() {
        // given
        telegramFeedService = newService();
        Channel channel = channelOf();
        LocalDate date = LocalDate.of(2026, 8, 15);
        TelegramDigest digest = digestOf(1L, channel, date, "요약1");
        given(telegramDigestRepository.findByIdWithChannel(1L)).willReturn(Optional.of(digest));
        given(telegramDigestTickerRepository.findByTelegramDigest(digest))
            .willReturn(List.of(TelegramDigestTicker.of(digest, "AAPL", "애플", "BULLISH", BigDecimal.valueOf(0.8))));
        given(telegramPostRepository.findByChannelAndStatusAndPublishedAtBetween(
            eq(channel), eq(TelegramPostStatus.SELECTED), any(), any()))
            .willReturn(List.of(
                TelegramPost.of(channel, "insidertracking/1", 1L, "본문", date.atTime(9, 0), 10L, LocalDateTime.now(), false)));

        // when
        TelegramFeedDigestDetailResponse result = telegramFeedService.getDigestDetail(1L);

        // then
        assertThat(result.summary()).isEqualTo("요약1");
        assertThat(result.keyPoints()).containsExactly("포인트1", "포인트2");
        assertThat(result.macroPoints()).containsExactly("매크로포인트1");
        assertThat(result.caveat()).isEqualTo("투자 권유 아님");
        assertThat(result.tickers()).hasSize(1);
        assertThat(result.sourcePostUrls()).containsExactly("https://t.me/insidertracking/1");
    }

    @Test
    @DisplayName("[macro_points 필드가 없는 과거 다이제스트 데이터를 조회해도 빈 리스트로 방어한다]")
    void getDigestDetail_payloadWithoutMacroPoints_defaultsToEmptyList() {
        // given
        telegramFeedService = newService();
        Channel channel = channelOf();
        TelegramDigest digest = digestOfWithoutMacroPoints(1L, channel, LocalDate.of(2026, 8, 15), "요약1");
        given(telegramDigestRepository.findByIdWithChannel(1L)).willReturn(Optional.of(digest));
        given(telegramDigestTickerRepository.findByTelegramDigest(digest)).willReturn(List.of());
        given(telegramPostRepository.findByChannelAndStatusAndPublishedAtBetween(
            eq(channel), eq(TelegramPostStatus.SELECTED), any(), any()))
            .willReturn(List.of());

        // when
        TelegramFeedDigestDetailResponse result = telegramFeedService.getDigestDetail(1L);

        // then
        assertThat(result.macroPoints()).isEmpty();
    }

    @Test
    @DisplayName("[존재하지 않는 다이제스트면 NotFoundException을 던진다]")
    void getDigestDetail_notFound_throwsNotFoundException() {
        // given
        telegramFeedService = newService();
        given(telegramDigestRepository.findByIdWithChannel(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> telegramFeedService.getDigestDetail(999L))
            .isInstanceOf(NotFoundException.class);
    }
}
