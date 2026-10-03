package com.quantlime.videofeed.service;

import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.implement.ProdTranscriptSyncer;
import com.quantlime.videofeed.implement.TranscriptReader;
import com.quantlime.videofeed.implement.VideoReader;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;

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

    private final VideoReader videoReader;
    private final TranscriptReader transcriptReader;
    private final ProdTranscriptSyncer prodTranscriptSyncer;

    public void syncOne(Long videoId) {
        if (!prodTranscriptSyncer.isConfigured()) {
            log.debug("운영 동기화 미설정(sync.api-key/prod-api-base) - 스킵: videoId={}", videoId);
            return;
        }
        Video video = videoReader.findById(videoId).orElse(null);
        Transcript transcript = video != null
            ? transcriptReader.findByVideo(video).orElse(null) : null;
        if (video == null || transcript == null) {
            log.warn("운영 동기화 대상 영상/자막을 찾을 수 없음 - 스킵: videoId={}", videoId);
            return;
        }
        try {
            prodTranscriptSyncer.push(video, transcript);
            log.info("자막 운영 동기화 완료: videoId={}, externalVideoId={}", videoId, video.getExternalVideoId());
        } catch (Exception e) {
            log.warn("자막 운영 동기화 실패(다음 배치 스크립트가 보충함): videoId={}, reason={}{}",
                videoId, e.getMessage(), connectionDownHint(e));
        }
    }

    // 연결 거부/타임아웃은 실제 장애보다 "운영 서버를 비용 절감 목적으로
    // 꺼둔 상태"일 가능성이 커서(2026-09-15 도입 당시부터 상시 기동을
    // 전제하지 않음) 원인 조사 시간을 아끼기 위해 로그에 바로 힌트를 남긴다.
    private static String connectionDownHint(Throwable e) {
        Throwable rootCause = NestedExceptionUtils.getRootCause(e);
        if (rootCause instanceof ConnectException || rootCause instanceof SocketTimeoutException) {
            return " (운영 서버가 비용 절감을 위해 꺼져 있을 수 있음 - 장애 아닐 가능성 높음)";
        }
        return "";
    }
}
