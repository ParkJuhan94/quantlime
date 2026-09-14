package com.quantlime.videofeed.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.SummarizeApiRequest;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.videofeed.repository.TranscriptRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 영상 한 건의 AI 요약 생성을 실제로 수행한다(2026-09-14, Kafka 이벤트화로
 * {@code SummaryCollectionFacade}에서 분리) - {@link TranscriptProcessingService}와
 * 동일한 패턴. {@code event} 모듈의 {@code SummarizeRequestConsumer}가
 * {@code video.transcribed} 메시지를 받을 때마다 이 메서드를 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryProcessingService {

    private final VideoRepository videoRepository;
    private final TranscriptRepository transcriptRepository;
    private final PythonEngineClient pythonEngineClient;
    private final SummaryPersistService summaryPersistService;

    public void processVideo(Long videoId) {
        Video video = videoRepository.findByIdWithChannel(videoId).orElse(null);
        if (video == null) {
            log.warn("요약 처리 대상 영상을 찾을 수 없음(삭제됐을 수 있음) - 스킵: videoId={}", videoId);
            return;
        }
        if (video.getStatus() == VideoStatus.SUMMARIZED) {
            log.debug("이미 요약된 영상 - 중복 이벤트 스킵: videoId={}", videoId);
            return;
        }
        try {
            Transcript transcript = transcriptRepository.findByVideo(video)
                .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_TRANSCRIPT));
            SummarizeApiResponse response = pythonEngineClient.summarize(new SummarizeApiRequest(
                video.getTitle(), video.getChannel().getName(), transcript.getContent()));
            summaryPersistService.persistResult(video.getId(), response);
        } catch (Exception e) {
            log.error("AI 요약 생성 실패: videoId={}, title={}, reason={}",
                video.getId(), video.getTitle(), e.getMessage(), e);
            summaryPersistService.markSummarizeFailed(video.getId(), e.getMessage());
            throw e;
        }
    }
}
