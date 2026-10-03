package com.quantlime.videofeed.implement;

import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.SummarizeApiRequest;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 영상 한 건의 AI 요약을 퀀트 엔진(Gemini)에 요청하는 구현 레이어(Implementation) -
 * 도메인 객체를 엔진 요청으로 바꾸고 호출한다. 실패 예외는 그대로 던진다(Kafka
 * {@code @RetryableTopic}이 재시도 여부를 판단하므로). 저장은 {@link SummaryAppender}.
 */
@Component
@RequiredArgsConstructor
public class SummaryGenerator {

    private final PythonEngineClient pythonEngineClient;

    public SummarizeApiResponse generate(Video video, Transcript transcript) {
        return pythonEngineClient.summarize(new SummarizeApiRequest(
            video.getTitle(), video.getChannel().getName(), transcript.getContent()));
    }
}
