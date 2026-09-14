package com.quantlime.videofeed.service;

import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.TranscribeApiRequest;
import com.quantlime.infra.python.dto.TranscribeApiResponse;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 영상 한 건의 자막 조회를 실제로 수행한다(2026-09-14, Kafka 이벤트화로
 * {@code TranscriptCollectionFacade}에서 분리) - {@code event} 모듈의
 * {@code TranscriptRequestConsumer}가 {@code video.selected} 메시지를 받을
 * 때마다 이 메서드를 호출한다.
 *
 * <p>실패 시 예외를 그대로 다시 던진다 - Spring Kafka {@code @RetryableTopic}이
 * 이 예외를 보고 재시도 토픽으로 넘길지 판단하므로, 여기서 삼켜버리면
 * 재시도 자체가 동작하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptProcessingService {

    private final VideoRepository videoRepository;
    private final PythonEngineClient pythonEngineClient;
    private final TranscriptPersistService transcriptPersistService;

    public void processVideo(Long videoId) {
        Video video = videoRepository.findById(videoId).orElse(null);
        if (video == null) {
            log.warn("자막 처리 대상 영상을 찾을 수 없음(삭제됐을 수 있음) - 스킵: videoId={}", videoId);
            return;
        }
        // 재발행 배치(publishBacklog)와 실시간 이벤트가 겹치거나, 재시도 도중
        // 다른 경로로 이미 처리된 경우를 방어한다 - 중복 이벤트에 안전해야
        // Redis 락 없이도 동시 실행이 가능하다.
        if (video.getStatus() == VideoStatus.TRANSCRIBED || video.getStatus() == VideoStatus.SUMMARIZED) {
            log.debug("이미 처리된 영상 - 중복 이벤트 스킵: videoId={}, status={}", videoId, video.getStatus());
            return;
        }
        try {
            TranscribeApiResponse response = pythonEngineClient.fetchTranscript(
                new TranscribeApiRequest(video.getExternalVideoId()));
            transcriptPersistService.persistResult(video.getId(), response);
        } catch (Exception e) {
            log.error("자막 수집 실패: videoId={}, title={}, reason={}",
                video.getId(), video.getTitle(), e.getMessage(), e);
            transcriptPersistService.markFetchFailed(video.getId(), e.getMessage());
            throw e;
        }
    }
}
