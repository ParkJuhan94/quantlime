package com.quantlime.videofeed.service;

import com.quantlime.infra.python.dto.TranscribeApiResponse;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.implement.TranscriptAppender;
import com.quantlime.videofeed.implement.TranscriptFetcher;
import com.quantlime.videofeed.implement.VideoReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * <p>{@code videofeed.transcript.fetch-enabled=false}면 아무것도 하지 않고 돌아온다(2026-10-24, 운영 프로파일에서 꺼 둠).
 * 운영 서버 IP는 YouTube가 차단해(RequestBlocked) 자체 수집이 한 번도 성공하지 못하고(최근 15시간 11건 전부 HTTP 500),
 * 영상마다 4번 재시도 후 DLT로 가며 엔진 스레드와 서킷브레이커({@code quant-engine-transcript})만 소모했다. 자막은 로컬에서
 * 수집해 운영으로 동기화하며({@code TranscriptImportService}) 그 임포트는 SELECTED/FAILED 영상을 모두 받으므로, 꺼진 동안은
 * 영상 상태를 건드리지 않고(실패 처리·예외·DLT 없음) 동기화를 기다리게 둔다. 프록시 등으로 운영 IP 문제가 풀리면
 * application-prod.yml 한 줄로 다시 켠다.
 */
@Slf4j
@Service
public class TranscriptProcessingService {

    private final VideoReader videoReader;
    private final TranscriptFetcher transcriptFetcher;
    private final TranscriptAppender transcriptAppender;
    private final boolean fetchEnabled;

    public TranscriptProcessingService(VideoReader videoReader, TranscriptFetcher transcriptFetcher,
                                       TranscriptAppender transcriptAppender,
                                       @Value("${videofeed.transcript.fetch-enabled:true}") boolean fetchEnabled) {
        this.videoReader = videoReader;
        this.transcriptFetcher = transcriptFetcher;
        this.transcriptAppender = transcriptAppender;
        this.fetchEnabled = fetchEnabled;
    }

    public void processVideo(Long videoId) {
        if (!fetchEnabled) {
            log.info("자막 자체 수집이 꺼져 있어 로컬 동기화를 기다림(videofeed.transcript.fetch-enabled=false): videoId={}",
                videoId);
            return;
        }
        Video video = videoReader.findById(videoId).orElse(null);
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
            TranscribeApiResponse response = transcriptFetcher.fetch(video);
            transcriptAppender.persistResult(video.getId(), response);
        } catch (Exception e) {
            log.error("자막 수집 실패: videoId={}, title={}, reason={}",
                video.getId(), video.getTitle(), e.getMessage(), e);
            transcriptAppender.markFetchFailed(video.getId(), e.getMessage());
            throw e;
        }
    }
}
