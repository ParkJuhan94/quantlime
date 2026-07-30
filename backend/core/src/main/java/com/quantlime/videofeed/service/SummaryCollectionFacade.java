package com.quantlime.videofeed.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.SummarizeApiRequest;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.dto.SummarizeResult;
import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.videofeed.repository.TranscriptRepository;
import com.quantlime.videofeed.repository.VideoRepository;
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
 * TRANSCRIBED(+ 재시도 상한 이내 FAILED) 영상을 배치로 돌며 AI 요약을 생성한다.
 * TranscriptCollectionFacade와 동일하게 영상 단위로 장애를 격리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryCollectionFacade {

    private static final int MAX_RETRY_COUNT = 3;
    private static final int BATCH_SIZE = 20;
    private static final String LOCK_KEY = "lock:summary-collect";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final RedisLockService redisLockService;
    private final VideoRepository videoRepository;
    private final TranscriptRepository transcriptRepository;
    private final PythonEngineClient pythonEngineClient;
    private final SummaryPersistService summaryPersistService;

    /**
     * 락을 잡은 채로 runBatch()를 실행한다. SummaryCollectionScheduler·
     * FeedCollectionAdminController(수동 트리거) 둘 다 이 진입점만 호출해야
     * 같은 후보 영상을 동시에 집어 Gemini 요약 API를 중복 호출하는 걸 막을
     * 수 있다 - 이 계정의 무료 티어가 일 20회로 배치 크기(20)와 같아, 중복
     * 실행 한 번이 그날 쿼터를 통째로 태울 수 있다(CLAUDE.md Phase 8 참고).
     */
    public Optional<List<SummarizeResult>> runBatchExclusively() {
        return redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, this::runBatch);
    }

    // 락 없이 배치 로직만 실행한다 - SummaryCollectionFacadeTest가 락 유무와
    // 무관하게 배치 처리 자체를 검증할 때 직접 호출한다. 스케줄러/관리자
    // 엔드포인트는 반드시 runBatchExclusively()를 통해서만 호출할 것.
    public List<SummarizeResult> runBatch() {
        Slice<Video> candidates = videoRepository.findSummarizeCandidates(
            List.of(VideoStatus.TRANSCRIBED, VideoStatus.FAILED), MAX_RETRY_COUNT,
            PageRequest.of(0, BATCH_SIZE));

        List<SummarizeResult> results = new ArrayList<>();
        for (Video video : candidates) {
            results.add(processVideo(video));
        }
        return results;
    }

    private SummarizeResult processVideo(Video video) {
        try {
            Transcript transcript = transcriptRepository.findByVideo(video)
                .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_TRANSCRIPT));
            SummarizeApiResponse response = pythonEngineClient.summarize(new SummarizeApiRequest(
                video.getTitle(), video.getChannel().getName(), transcript.getContent()));
            summaryPersistService.persistResult(video.getId(), response);
            return SummarizeResult.success(video.getId());
        } catch (Exception e) {
            log.error("AI 요약 생성 실패: videoId={}, title={}, reason={}",
                video.getId(), video.getTitle(), e.getMessage(), e);
            summaryPersistService.markSummarizeFailed(video.getId(), e.getMessage());
            return SummarizeResult.failed(video.getId(), e.getMessage());
        }
    }
}
