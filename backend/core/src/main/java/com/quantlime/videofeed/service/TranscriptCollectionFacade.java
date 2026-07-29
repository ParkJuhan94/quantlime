package com.quantlime.videofeed.service;

import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.TranscribeApiRequest;
import com.quantlime.infra.python.dto.TranscribeApiResponse;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.dto.TranscribeResult;
import com.quantlime.videofeed.repository.VideoRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

/**
 * SELECTED(+ 재시도 상한 이내 FAILED) 영상을 배치로 돌며 자막을 수집한다.
 * 채널 수집(FeedCollectionFacade)과 동일하게 영상 단위로 장애를 격리한다 -
 * 한 영상의 실패(IP 차단, 네트워크 오류 등)가 나머지 배치를 막지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptCollectionFacade {

    private static final int MAX_RETRY_COUNT = 3;
    private static final int BATCH_SIZE = 20;

    private final VideoRepository videoRepository;
    private final PythonEngineClient pythonEngineClient;
    private final TranscriptPersistService transcriptPersistService;

    public List<TranscribeResult> runBatch() {
        Slice<Video> candidates = videoRepository.findTranscribeCandidates(
            List.of(VideoStatus.SELECTED, VideoStatus.FAILED), MAX_RETRY_COUNT,
            PageRequest.of(0, BATCH_SIZE));

        List<TranscribeResult> results = new ArrayList<>();
        for (Video video : candidates) {
            results.add(processVideo(video));
        }
        return results;
    }

    private TranscribeResult processVideo(Video video) {
        try {
            TranscribeApiResponse response = pythonEngineClient.fetchTranscript(
                new TranscribeApiRequest(video.getExternalVideoId()));
            transcriptPersistService.persistResult(video.getId(), response);
            return response.available()
                ? TranscribeResult.success(video.getId())
                : TranscribeResult.unavailable(video.getId(), response.reason());
        } catch (Exception e) {
            log.error("자막 수집 실패: videoId={}, title={}, reason={}",
                video.getId(), video.getTitle(), e.getMessage(), e);
            transcriptPersistService.markFetchFailed(video.getId(), e.getMessage());
            return TranscribeResult.failed(video.getId(), e.getMessage());
        }
    }
}
