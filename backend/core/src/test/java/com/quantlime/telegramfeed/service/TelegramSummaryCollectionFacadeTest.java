package com.quantlime.telegramfeed.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.SummarizeApiRequest;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.infra.python.exception.PythonEngineErrorCode;
import com.quantlime.telegramfeed.dto.TelegramSummarizeResult;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
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
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramSummaryCollectionFacadeTest {

    @Mock
    private RedisLockService redisLockService;

    @Mock
    private TelegramPostRepository telegramPostRepository;

    @Mock
    private PythonEngineClient pythonEngineClient;

    @Mock
    private TelegramSummaryPersistService telegramSummaryPersistService;

    @InjectMocks
    private TelegramSummaryCollectionFacade telegramSummaryCollectionFacade;

    private TelegramPost postOf(Long id, String content) {
        Channel channel = Channel.ofTelegram("insidertracking", "테스트 채널", 30,
            new TelegramFilterConfig(300, 2, List.of(), List.of()));
        TelegramPost post = TelegramPost.of(channel, "insidertracking/" + id, id, content,
            LocalDateTime.now(), 100L, LocalDateTime.now(), false);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    @Test
    @DisplayName("[요약 생성 성공 시 결과를 영속화하고 source_kind=telegram, video_title=null로 호출한다]")
    void runBatch_success_persistsAndCallsWithTelegramSourceKind() {
        // given
        TelegramPost post = postOf(1L, "본문1");
        given(telegramPostRepository.findSummarizeCandidates(any(), anyInt(), any()))
            .willReturn(new SliceImpl<>(List.of(post)));
        SummarizeApiResponse response = new SummarizeApiResponse(
            "요약", List.of(), List.of(), List.of(), "고지", "gemini-3.5-flash-lite", 100, 50);
        given(pythonEngineClient.summarize(new SummarizeApiRequest(null, "테스트 채널", "본문1", "telegram")))
            .willReturn(response);

        // when
        List<TelegramSummarizeResult> results = telegramSummaryCollectionFacade.runBatch();

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        verify(telegramSummaryPersistService).persistResult(1L, response);
    }

    @Test
    @DisplayName("[한 글의 요약 실패가 나머지 배치 처리를 막지 않는다(장애 격리)]")
    void runBatch_onePostFails_isolatesFailureAndContinuesBatch() {
        // given
        TelegramPost failing = postOf(1L, "실패본문");
        TelegramPost succeeding = postOf(2L, "성공본문");
        given(telegramPostRepository.findSummarizeCandidates(any(), anyInt(), any()))
            .willReturn(new SliceImpl<>(List.of(failing, succeeding)));
        given(pythonEngineClient.summarize(new SummarizeApiRequest(null, "테스트 채널", "실패본문", "telegram")))
            .willThrow(new ExternalApiException(PythonEngineErrorCode.SUMMARY_GENERATION_FAILED));
        SummarizeApiResponse okResponse = new SummarizeApiResponse(
            "요약", List.of(), List.of(), List.of(), "고지", "gemini-3.5-flash-lite", 100, 50);
        given(pythonEngineClient.summarize(new SummarizeApiRequest(null, "테스트 채널", "성공본문", "telegram")))
            .willReturn(okResponse);

        // when
        List<TelegramSummarizeResult> results = telegramSummaryCollectionFacade.runBatch();

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(1).success()).isTrue();
        verify(telegramSummaryPersistService).markSummarizeFailed(eq(1L), any());
        verify(telegramSummaryPersistService).persistResult(2L, okResponse);
    }

    @Test
    @DisplayName("[runBatchExclusively는 락 획득에 실패하면 배치를 실행하지 않고 빈 Optional을 반환한다]")
    void runBatchExclusively_whenLockNotAcquired_skipsBatch() {
        // given
        given(redisLockService.runExclusively(any(), any(), any())).willReturn(Optional.empty());

        // when
        Optional<List<TelegramSummarizeResult>> result = telegramSummaryCollectionFacade.runBatchExclusively();

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(pythonEngineClient);
    }

    @Test
    @DisplayName("[runBatchExclusively는 락을 획득하면 runBatch 결과를 감싼 Optional을 반환한다]")
    void runBatchExclusively_whenLockAcquired_returnsBatchResult() {
        // given
        TelegramPost post = postOf(1L, "본문1");
        given(telegramPostRepository.findSummarizeCandidates(any(), anyInt(), any()))
            .willReturn(new SliceImpl<>(List.of(post)));
        SummarizeApiResponse response = new SummarizeApiResponse(
            "요약", List.of(), List.of(), List.of(), "고지", "gemini-3.5-flash-lite", 100, 50);
        given(pythonEngineClient.summarize(new SummarizeApiRequest(null, "테스트 채널", "본문1", "telegram")))
            .willReturn(response);
        given(redisLockService.runExclusively(any(), any(), any())).willAnswer(invocation -> {
            Supplier<List<TelegramSummarizeResult>> task = invocation.getArgument(2);
            return Optional.of(task.get());
        });

        // when
        Optional<List<TelegramSummarizeResult>> result = telegramSummaryCollectionFacade.runBatchExclusively();

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        verify(telegramSummaryPersistService).persistResult(1L, response);
    }
}
