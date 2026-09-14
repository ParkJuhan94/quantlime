package com.quantlime.videofeed.service;

import com.quantlime.infra.sync.SyncApiClient;
import com.quantlime.infra.sync.SyncProperties;
import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.dto.request.TranscriptImportRequest;
import com.quantlime.videofeed.repository.TranscriptRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 로컬에서 자막이 새로 저장될 때마다(LocalTranscriptSyncConsumer, event
 * 모듈) 운영 서버로 즉시 밀어넣는다(2026-09-15) - youtube-transcript-api가
 * 운영 IP를 차단해 운영에서 직접 자막을 못 가져오는 문제의 실시간 우회로.
 * 실패해도 예외를 위로 던지지 않는다(로그만 남김) - 이 동기화는 "되면
 * 좋은" 부가 경로이지 로컬 자막 파이프라인 자체의 필수 단계가 아니고,
 * 운영이 잠시 내려가 있거나 네트워크가 끊긴 정도로 로컬 컨슈머를 막히게
 * 하고 싶지 않다. 놓친 건(prodApiBase 미설정 기간 포함)
 * {@code scripts/sync-transcripts-to-prod.sh}로 나중에 일괄 보충한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalTranscriptSyncService {

    private final VideoRepository videoRepository;
    private final TranscriptRepository transcriptRepository;
    private final SyncApiClient syncApiClient;
    private final SyncProperties syncProperties;

    public void syncOne(Long videoId) {
        if (!StringUtils.hasText(syncProperties.getApiKey())
            || !StringUtils.hasText(syncProperties.getProdApiBase())) {
            log.debug("운영 동기화 미설정(sync.api-key/prod-api-base) - 스킵: videoId={}", videoId);
            return;
        }
        Video video = videoRepository.findById(videoId).orElse(null);
        Transcript transcript = video != null
            ? transcriptRepository.findByVideo(video).orElse(null) : null;
        if (video == null || transcript == null) {
            log.warn("운영 동기화 대상 영상/자막을 찾을 수 없음 - 스킵: videoId={}", videoId);
            return;
        }
        try {
            syncApiClient.pushTranscript(new TranscriptImportRequest.Item(
                video.getExternalVideoId(), transcript.getSource(), transcript.getLang(),
                transcript.getContent(), transcript.getCharCount()));
            log.info("자막 운영 동기화 완료: videoId={}, externalVideoId={}", videoId, video.getExternalVideoId());
        } catch (Exception e) {
            log.warn("자막 운영 동기화 실패(다음 배치 스크립트가 보충함): videoId={}, reason={}",
                videoId, e.getMessage());
        }
    }
}
