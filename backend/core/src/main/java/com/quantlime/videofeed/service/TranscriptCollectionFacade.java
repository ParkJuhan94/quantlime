package com.quantlime.videofeed.service;

import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.event.VideoSelectedEvent;
import com.quantlime.videofeed.repository.VideoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

/**
 * 자막 조회는 이제 실시간 트리거(VideoFilterService가 markSelected() 시점에
 * 발행하는 VideoSelectedEvent → event 모듈 Kafka 컨슈머)로 처리된다
 * (2026-09-14 이벤트화). 이 클래스는 그 실시간 경로를 놓쳤을 수 있는 영상
 * (기동 중 서버가 꺼져있었던 경우 등)을 위한 **백로그 재발행 전용**으로
 * 축소됐다 - StartupCatchUpRunner(기동 캐치업)와
 * FeedCollectionAdminController(관리자 수동 트리거)만 호출한다.
 *
 * <p>quant-engine을 직접 부르지 않고 VideoSelectedEvent만 재발행하므로
 * Redis 락이 필요 없다 - TranscriptProcessingService가 이미 TRANSCRIBED인
 * 영상은 상태 가드로 스킵해 중복 발행에도 안전하다(멱등).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptCollectionFacade {

    private static final int MAX_RETRY_COUNT = 3;
    private static final int BATCH_SIZE = 20;

    private final VideoRepository videoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public int publishBacklog() {
        Slice<Video> candidates = videoRepository.findTranscribeCandidates(
            List.of(VideoStatus.SELECTED, VideoStatus.FAILED), MAX_RETRY_COUNT,
            PageRequest.of(0, BATCH_SIZE));
        candidates.forEach(video -> eventPublisher.publishEvent(new VideoSelectedEvent(video.getId())));
        int count = candidates.getNumberOfElements();
        log.info("자막 조회 이벤트 재발행: {}건", count);
        return count;
    }
}
