package com.quantlime.videofeed.service;

import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.event.VideoTranscribedEvent;
import com.quantlime.videofeed.repository.VideoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

/**
 * {@link TranscriptCollectionFacade}와 동일한 이유(2026-09-14 이벤트화)로
 * 백로그 재발행 전용으로 축소됐다 - AI 요약은 이제 실시간 트리거
 * (TranscriptPersistService가 markTranscribed() 시점에 발행하는
 * VideoTranscribedEvent → event 모듈 Kafka 컨슈머)로 처리된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryCollectionFacade {

    private static final int MAX_RETRY_COUNT = 3;
    private static final int BATCH_SIZE = 20;

    private final VideoRepository videoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public int publishBacklog() {
        Slice<Video> candidates = videoRepository.findSummarizeCandidates(
            List.of(VideoStatus.TRANSCRIBED, VideoStatus.FAILED), MAX_RETRY_COUNT,
            PageRequest.of(0, BATCH_SIZE));
        candidates.forEach(video -> eventPublisher.publishEvent(new VideoTranscribedEvent(video.getId())));
        int count = candidates.getNumberOfElements();
        log.info("AI 요약 이벤트 재발행: {}건", count);
        return count;
    }
}
