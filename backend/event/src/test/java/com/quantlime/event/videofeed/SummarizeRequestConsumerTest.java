package com.quantlime.event.videofeed;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.quantlime.event.observability.KafkaDltNotifier;
import com.quantlime.videofeed.service.SummaryProcessingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link TranscriptRequestConsumerTest}와 동일한 이유(무한 재발행 루프 방지 불변식 회귀 테스트). */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SummarizeRequestConsumerTest {

    @Mock
    private SummaryProcessingService summaryProcessingService;

    @Mock
    private KafkaDltNotifier dltNotifier;

    @InjectMocks
    private SummarizeRequestConsumer summarizeRequestConsumer;

    @Test
    @DisplayName("[onDlt는 정상 케이스에서 dltNotifier.notify를 videofeed-summary 도메인으로 호출한다]")
    void onDlt_happyPath_notifiesDlt() {
        // given
        VideoTranscribedMessage message = VideoTranscribedMessage.of(1L);

        // when
        summarizeRequestConsumer.onDlt(message);

        // then
        verify(dltNotifier).notify(eq("videofeed-summary"), eq(VideoFeedTopics.VIDEO_TRANSCRIBED), anyString());
    }

    @Test
    @DisplayName("[onDlt는 dltNotifier.notify 자체가 실패해도 예외를 전파하지 않는다 - 무한 재발행 루프 방지선]")
    void onDlt_dltNotifierThrows_doesNotPropagate() {
        // given
        VideoTranscribedMessage message = VideoTranscribedMessage.of(1L);
        willThrow(new RuntimeException("slack down")).given(dltNotifier).notify(any(), any(), any());

        // when & then
        assertThatCode(() -> summarizeRequestConsumer.onDlt(message)).doesNotThrowAnyException();
    }
}
