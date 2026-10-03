package com.quantlime.telegramfeed.implement;

import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.SummarizeApiRequest;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.videofeed.domain.Channel;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 채널×날짜의 선별된 글을 퀀트 엔진(Gemini)에 보내 다이제스트 요약을 만드는 구현
 * 레이어(Implementation) - 글을 시간순으로 합치고 엔진 요청으로 바꿔 호출한다. 실패
 * 예외는 그대로 던진다(Kafka {@code @RetryableTopic}이 재시도 여부를 판단하므로). 저장은
 * {@link TelegramDigestAppender}.
 */
@Component
@RequiredArgsConstructor
public class TelegramDigestGenerator {

    private static final String CONTENT_SEPARATOR = "\n\n---\n\n";

    private final PythonEngineClient pythonEngineClient;

    public SummarizeApiResponse generate(Channel channel, List<TelegramPost> posts) {
        String combinedContent = posts.stream()
            .sorted(Comparator.comparing(TelegramPost::getPublishedAt))
            .map(TelegramPost::getContent)
            .collect(Collectors.joining(CONTENT_SEPARATOR));
        return pythonEngineClient.summarize(
            new SummarizeApiRequest(null, channel.getName(), combinedContent, "telegram"));
    }
}
