package com.quantlime.telegramfeed.service;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.SummarizeApiRequest;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.telegramfeed.dto.TelegramSummarizeResult;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

/**
 * SELECTED(+ 재시도 상한 이내 FAILED) 텔레그램 글을 배치로 돌며 AI 요약을 생성한다
 * (SummaryCollectionFacade와 동일 구조). BATCH_SIZE를 유튜브(20)보다 훨씬 작게
 * 잡은 이유는 Gemini 무료 티어 일일 쿼터를 유튜브와 공유하기 때문 - 필터 단계의
 * max_per_run(2/채널·일)으로 이미 수요 자체를 하루 최대 4건으로 구조적으로
 * 제한해뒀지만, 백로그 재처리 시 한 번에 쿼터를 태우지 않도록 배치 크기도
 * 별도로 낮춘다(docs/ROADMAP.md "Gemini 무료 티어 일 20회 쿼터 공유" 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramSummaryCollectionFacade {

    private static final int MAX_RETRY_COUNT = 3;
    private static final int BATCH_SIZE = 5;
    private static final String LOCK_KEY = "lock:telegram-summary-collect";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final RedisLockService redisLockService;
    private final TelegramPostRepository telegramPostRepository;
    private final PythonEngineClient pythonEngineClient;
    private final TelegramSummaryPersistService telegramSummaryPersistService;

    public Optional<List<TelegramSummarizeResult>> runBatchExclusively() {
        return redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, this::runBatch);
    }

    // 락 없이 배치 로직만 실행 - 테스트에서 직접 호출. 스케줄러/관리자 엔드포인트는
    // 반드시 runBatchExclusively()를 통해서만 호출할 것(SummaryCollectionFacade와 동일).
    public List<TelegramSummarizeResult> runBatch() {
        Slice<TelegramPost> candidates = telegramPostRepository.findSummarizeCandidates(
            List.of(TelegramPostStatus.SELECTED, TelegramPostStatus.FAILED), MAX_RETRY_COUNT,
            PageRequest.of(0, BATCH_SIZE));

        List<TelegramSummarizeResult> results = new ArrayList<>();
        for (TelegramPost post : candidates) {
            results.add(processPost(post));
        }
        return results;
    }

    private TelegramSummarizeResult processPost(TelegramPost post) {
        try {
            SummarizeApiResponse response = pythonEngineClient.summarize(new SummarizeApiRequest(
                null, post.getChannel().getName(), post.getContent(), "telegram"));
            telegramSummaryPersistService.persistResult(post.getId(), response);
            return TelegramSummarizeResult.success(post.getId());
        } catch (Exception e) {
            log.error("텔레그램 AI 요약 생성 실패: telegramPostId={}, reason={}",
                post.getId(), e.getMessage(), e);
            telegramSummaryPersistService.markSummarizeFailed(post.getId(), e.getMessage());
            return TelegramSummarizeResult.failed(post.getId(), e.getMessage());
        }
    }
}
