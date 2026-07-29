package com.quantlime.videofeed.service;

import com.quantlime.common.exception.NotFoundException;
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
import java.util.ArrayList;
import java.util.List;
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

    private final VideoRepository videoRepository;
    private final TranscriptRepository transcriptRepository;
    private final PythonEngineClient pythonEngineClient;
    private final SummaryPersistService summaryPersistService;

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
