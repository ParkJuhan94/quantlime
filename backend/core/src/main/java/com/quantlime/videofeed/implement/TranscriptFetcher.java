package com.quantlime.videofeed.implement;

import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.TranscribeApiRequest;
import com.quantlime.infra.python.dto.TranscribeApiResponse;
import com.quantlime.videofeed.domain.Video;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 영상 한 건의 자막을 퀀트 엔진(youtube-transcript-api)에 요청하는 구현 레이어
 * (Implementation). 실패 예외는 그대로 던진다(Kafka {@code @RetryableTopic}이 재시도
 * 여부를 판단하므로). 저장은 {@link TranscriptAppender}.
 */
@Component
@RequiredArgsConstructor
public class TranscriptFetcher {

    private final PythonEngineClient pythonEngineClient;

    public TranscribeApiResponse fetch(Video video) {
        return pythonEngineClient.fetchTranscript(new TranscribeApiRequest(video.getExternalVideoId()));
    }
}
