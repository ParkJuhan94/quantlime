package com.quantlime.telegramfeed.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.SummarizeApiRequest;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.infra.python.exception.PythonEngineErrorCode;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.telegramfeed.dto.TelegramDigestGenerateResult;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramDigestGenerationFacadeTest {

    @Mock
    private RedisLockService redisLockService;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private TelegramPostRepository telegramPostRepository;

    @Mock
    private PythonEngineClient pythonEngineClient;

    @Mock
    private TelegramDigestPersistService telegramDigestPersistService;

    @InjectMocks
    private TelegramDigestGenerationFacade telegramDigestGenerationFacade;

    private Channel channelOf(Long id, String handle) {
        Channel channel = Channel.ofTelegram(handle, "테스트 채널", 30,
            new TelegramFilterConfig(300, List.of(), List.of()));
        ReflectionTestUtils.setField(channel, "id", id);
        return channel;
    }

    private TelegramPost postOf(Channel channel, long messageId, String content, LocalDateTime publishedAt) {
        return TelegramPost.of(channel, channel.getExternalChannelId() + "/" + messageId, messageId, content,
            publishedAt, 100L, LocalDateTime.now(), false);
    }

    @Test
    @DisplayName("[그날 SELECTED된 글이 여러 건이면 발행시각순으로 구분선(---)을 넣어 합쳐 한 번의 요약 호출로 다이제스트를 생성한다]")
    void runAll_multiplePostsSameDay_combinesInPublishedOrderAndGeneratesOneDigest() {
        // given
        Channel channel = channelOf(1L, "insidertracking");
        given(channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.TELEGRAM))
            .willReturn(List.of(channel));
        TelegramPost earlier = postOf(channel, 1L, "아침 게시글", LocalDateTime.of(2026, 8, 15, 8, 0));
        TelegramPost later = postOf(channel, 2L, "오후 게시글", LocalDateTime.of(2026, 8, 15, 14, 0));
        given(telegramPostRepository.findByChannelAndStatusAndPublishedAtBetween(
            eq(channel), eq(TelegramPostStatus.SELECTED), any(), any()))
            .willReturn(List.of(later, earlier));
        SummarizeApiResponse response = new SummarizeApiResponse(
            "오늘의 요약", List.of(), List.of(), List.of(), "고지", "gemini-3.5-flash-lite", 100, 50);
        given(pythonEngineClient.summarize(
            new SummarizeApiRequest(null, "테스트 채널", "아침 게시글\n\n---\n\n오후 게시글", "telegram")))
            .willReturn(response);

        // when
        List<TelegramDigestGenerateResult> results = telegramDigestGenerationFacade.runAll();

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).sourcePostCount()).isEqualTo(2);
        verify(telegramDigestPersistService).persistResult(eq(channel), eq(LocalDate.now()), eq(response));
    }

    @Test
    @DisplayName("[그날 SELECTED된 글이 없으면 요약을 호출하지 않고 스킵 결과를 반환한다]")
    void runAll_noEligiblePosts_skipsWithoutCallingSummarize() {
        // given
        Channel channel = channelOf(1L, "insidertracking");
        given(channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.TELEGRAM))
            .willReturn(List.of(channel));
        given(telegramPostRepository.findByChannelAndStatusAndPublishedAtBetween(
            eq(channel), eq(TelegramPostStatus.SELECTED), any(), any()))
            .willReturn(List.of());

        // when
        List<TelegramDigestGenerateResult> results = telegramDigestGenerationFacade.runAll();

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).reason()).isEqualTo("NO_ELIGIBLE_POSTS");
        verifyNoInteractions(pythonEngineClient, telegramDigestPersistService);
    }

    @Test
    @DisplayName("[한 채널의 다이제스트 생성 실패가 나머지 채널 처리를 막지 않는다(장애 격리)]")
    void runAll_oneChannelFails_isolatesFailureAndContinues() {
        // given
        Channel failingChannel = channelOf(1L, "failing");
        Channel okChannel = channelOf(2L, "ok");
        given(channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.TELEGRAM))
            .willReturn(List.of(failingChannel, okChannel));
        given(telegramPostRepository.findByChannelAndStatusAndPublishedAtBetween(
            eq(failingChannel), eq(TelegramPostStatus.SELECTED), any(), any()))
            .willReturn(List.of(postOf(failingChannel, 1L, "본문", LocalDateTime.now())));
        given(telegramPostRepository.findByChannelAndStatusAndPublishedAtBetween(
            eq(okChannel), eq(TelegramPostStatus.SELECTED), any(), any()))
            .willReturn(List.of(postOf(okChannel, 1L, "본문2", LocalDateTime.now())));
        given(pythonEngineClient.summarize(new SummarizeApiRequest(null, "테스트 채널", "본문", "telegram")))
            .willThrow(new ExternalApiException(PythonEngineErrorCode.SUMMARY_GENERATION_FAILED));
        SummarizeApiResponse okResponse = new SummarizeApiResponse(
            "요약", List.of(), List.of(), List.of(), "고지", "gemini-3.5-flash-lite", 100, 50);
        given(pythonEngineClient.summarize(new SummarizeApiRequest(null, "테스트 채널", "본문2", "telegram")))
            .willReturn(okResponse);

        // when
        List<TelegramDigestGenerateResult> results = telegramDigestGenerationFacade.runAll();

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(1).success()).isTrue();
        verify(telegramDigestPersistService).persistResult(eq(okChannel), eq(LocalDate.now()), eq(okResponse));
    }

    @Test
    @DisplayName("[runAllExclusively는 락 획득에 실패하면 배치를 실행하지 않고 빈 Optional을 반환한다]")
    void runAllExclusively_whenLockNotAcquired_skipsBatch() {
        // given
        given(redisLockService.runExclusively(any(), any(), any())).willReturn(Optional.empty());

        // when
        Optional<List<TelegramDigestGenerateResult>> result = telegramDigestGenerationFacade.runAllExclusively();

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(pythonEngineClient);
    }

    @Test
    @DisplayName("[runAllExclusively는 락을 획득하면 runAll 결과를 감싼 Optional을 반환한다]")
    void runAllExclusively_whenLockAcquired_returnsResult() {
        // given
        Channel channel = channelOf(1L, "insidertracking");
        given(channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.TELEGRAM))
            .willReturn(List.of(channel));
        given(telegramPostRepository.findByChannelAndStatusAndPublishedAtBetween(
            eq(channel), eq(TelegramPostStatus.SELECTED), any(), any()))
            .willReturn(List.of());
        given(redisLockService.runExclusively(any(), any(), any())).willAnswer(invocation -> {
            Supplier<List<TelegramDigestGenerateResult>> task = invocation.getArgument(2);
            return Optional.of(task.get());
        });

        // when
        Optional<List<TelegramDigestGenerateResult>> result = telegramDigestGenerationFacade.runAllExclusively();

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
    }
}
