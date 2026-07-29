package com.quantlime.videofeed.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.infra.python.dto.TranscribeApiResponse;
import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.videofeed.repository.TranscriptRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 퀀트 엔진 자막 조회(외부 I/O)와 결과 영속화를 분리한 짧은 트랜잭션 계층
 * (YoutubeVideoCollector/VideoPersistService와 동일한 설계). 호출부인
 * TranscriptCollectionFacade와 별도 빈으로 둔 것은 스타일 선택이 아니라
 * 실제 버그를 겪고 확정한 원칙이다 - ChannelVelocityInitializationService에서
 * 같은 클래스 내부의 @Transactional 메서드를 self-invocation으로 호출해
 * 프록시가 우회되고 변경분이 DB에 반영되지 않는 버그가 있었다(2026-07-27,
 * docs/CHANGELOG.md 참고). 별도 빈으로 분리하면 호출이 항상 프록시를 거쳐
 * 같은 문제가 구조적으로 재발하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TranscriptPersistService {

    private final VideoRepository videoRepository;
    private final TranscriptRepository transcriptRepository;

    @Transactional
    public void persistResult(Long videoId, TranscribeApiResponse response) {
        Video video = findVideo(videoId);
        if (!response.available()) {
            video.markFailed("자막 없음: " + response.reason());
            return;
        }
        transcriptRepository.save(Transcript.of(
            video, response.source(), response.lang(), response.content(), response.charCount()));
        video.markTranscribed();
    }

    @Transactional
    public void markFetchFailed(Long videoId, String reason) {
        findVideo(videoId).markFailed(reason);
    }

    private Video findVideo(Long videoId) {
        return videoRepository.findById(videoId)
            .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_VIDEO));
    }
}
