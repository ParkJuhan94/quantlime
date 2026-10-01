package com.quantlime.telegramfeed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.SummarizeApiRequest;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.infra.python.exception.PythonEngineErrorCode;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.telegramfeed.dto.TelegramDigestGenerateResult;
import com.quantlime.telegramfeed.event.TelegramDigestGenerationRequestedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 2026-09-30 카프카 다도메인 확장 Phase 4 이후: {@code publishAll}/{@code
 * runAllExclusively}는 채널마다 이벤트를 발행만 하므로 여기서는 "발행 건수/
 * 대상"만 검증한다. 실제 생성 로직(글 결합·스킵·Gemini 호출)과 그 실패
 * 전파는 {@code generateForChannel(Long, LocalDate)}(Kafka 컨슈머가 호출)
 * 쪽에서 검증한다 - 이전엔 순차 루프 안에서 예외를 삼켜 장애를 격리했지만,
 * 이제는 이 메서드가 예외를 그대로 던져야 Kafka 재시도/DLT가 동작한다.
 */
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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TelegramDigestGenerationFacade telegramDigestGenerationFacade;

    private Channel channelOf(Long id, String handle) {
        Channel channel = Channel.ofTelegram(handle, "테스트 채널", 30,
            new TelegramFilterConfig(300, List.of()));
        ReflectionTestUtils.setField(channel, "id", id);
        return channel;
    }

    private TelegramPost postOf(Channel channel, long messageId, String content, LocalDateTime publishedAt) {
        return TelegramPost.of(channel, channel.getExternalChannelId() + "/" + messageId, messageId, content,
            publishedAt, 100L, LocalDateTime.now(), false);
    }

    @Test
    @DisplayName("[publishAll은 활성화된 채널마다 오늘 날짜로 다이제스트 생성 이벤트를 발행하고 채널 수를 반환한다]")
    void publishAll_publishesOneEventPerEnabledChannel() {
        // given
        Channel channel1 = channelOf(1L, "insidertracking");
        Channel channel2 = channelOf(2L, "donmaek");
        given(channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.TELEGRAM))
            .willReturn(List.of(channel1, channel2));

        // when
        int count = telegramDigestGenerationFacade.publishAll();

        // then
        assertThat(count).isEqualTo(2);
        verify(eventPublisher).publishEvent(new TelegramDigestGenerationRequestedEvent(1L, LocalDate.now()));
        verify(eventPublisher).publishEvent(new TelegramDigestGenerationRequestedEvent(2L, LocalDate.now()));
    }

    @Test
    @DisplayName("[runAllExclusively는 락 획득에 실패하면 배치를 실행하지 않고 빈 Optional을 반환한다]")
    void runAllExclusively_whenLockNotAcquired_skipsBatch() {
        // given
        given(redisLockService.runExclusively(any(), any(), any())).willReturn(Optional.empty());

        // when
        Optional<Integer> result = telegramDigestGenerationFacade.runAllExclusively();

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("[runAllExclusively는 락을 획득하면 publishAll 결과(발행 건수)를 감싼 Optional을 반환한다]")
    void runAllExclusively_whenLockAcquired_returnsPublishedCount() {
        // given
        Channel channel = channelOf(1L, "insidertracking");
        given(channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.TELEGRAM))
            .willReturn(List.of(channel));
        given(redisLockService.runExclusively(any(), any(), any())).willAnswer(invocation -> {
            Supplier<Integer> task = invocation.getArgument(2);
            return Optional.of(task.get());
        });

        // when
        Optional<Integer> result = telegramDigestGenerationFacade.runAllExclusively();

        // then
        assertThat(result).contains(1);
    }

    @Test
    @DisplayName("[generateForChannel - 그날 SELECTED된 글이 여러 건이면 발행시각순으로 구분선(---)을 넣어 합쳐 한 번의 요약 호출로 다이제스트를 생성한다]")
    void generateForChannel_multiplePostsSameDay_combinesInPublishedOrderAndGeneratesOneDigest() {
        // given
        Channel channel = channelOf(1L, "insidertracking");
        LocalDate today = LocalDate.now();
        given(channelRepository.findById(1L)).willReturn(Optional.of(channel));
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
        TelegramDigestGenerateResult result = telegramDigestGenerationFacade.generateForChannel(1L, today);

        // then
        assertThat(result.success()).isTrue();
        assertThat(result.sourcePostCount()).isEqualTo(2);
        verify(telegramDigestPersistService).persistResult(eq(channel), eq(today), eq(response));
    }

    @Test
    @DisplayName("[generateForChannel - 그날 SELECTED된 글이 없으면 요약을 호출하지 않고 스킵 결과를 반환한다]")
    void generateForChannel_noEligiblePosts_skipsWithoutCallingSummarize() {
        // given
        Channel channel = channelOf(1L, "insidertracking");
        LocalDate today = LocalDate.now();
        given(channelRepository.findById(1L)).willReturn(Optional.of(channel));
        given(telegramPostRepository.findByChannelAndStatusAndPublishedAtBetween(
            eq(channel), eq(TelegramPostStatus.SELECTED), any(), any()))
            .willReturn(List.of());

        // when
        TelegramDigestGenerateResult result = telegramDigestGenerationFacade.generateForChannel(1L, today);

        // then
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).isEqualTo("NO_ELIGIBLE_POSTS");
        verifyNoInteractions(pythonEngineClient, telegramDigestPersistService);
    }

    @Test
    @DisplayName("[generateForChannel - 존재하지 않는 채널이면 NotFoundException을 던진다]")
    void generateForChannel_channelNotFound_throws() {
        // given
        given(channelRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> telegramDigestGenerationFacade.generateForChannel(99L, LocalDate.now()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[generateForChannel - 요약 생성 실패는 흡수하지 않고 그대로 던져 Kafka 재시도/DLT가 처리하게 한다]")
    void generateForChannel_summarizeFails_propagatesException() {
        // given
        Channel channel = channelOf(1L, "failing");
        given(channelRepository.findById(1L)).willReturn(Optional.of(channel));
        given(telegramPostRepository.findByChannelAndStatusAndPublishedAtBetween(
            eq(channel), eq(TelegramPostStatus.SELECTED), any(), any()))
            .willReturn(List.of(postOf(channel, 1L, "본문", LocalDateTime.now())));
        given(pythonEngineClient.summarize(new SummarizeApiRequest(null, "테스트 채널", "본문", "telegram")))
            .willThrow(new ExternalApiException(PythonEngineErrorCode.SUMMARY_GENERATION_FAILED));

        // when & then
        assertThatThrownBy(() -> telegramDigestGenerationFacade.generateForChannel(1L, LocalDate.now()))
            .isInstanceOf(ExternalApiException.class);
        verifyNoInteractions(telegramDigestPersistService);
    }
}
