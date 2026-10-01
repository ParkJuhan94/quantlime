package com.quantlime.event.videofeed;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.quantlime.event.observability.KafkaDltNotifier;
import com.quantlime.videofeed.service.TranscriptProcessingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code @DltHandler}는 절대 예외를 던지면 안 된다는 불변식(2026-09-30, 무한
 * 재발행 루프 실사고 - 클래스 주석 참고)의 회귀 테스트. 이 불변식이 코드
 * 리뷰만으로 지켜지길 기대하지 않고, 내부 협력 객체가 실패해도 onDlt 자신은
 * 절대 전파하지 않는지를 직접 검증한다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TranscriptRequestConsumerTest {

    @Mock
    private TranscriptProcessingService transcriptProcessingService;

    @Mock
    private KafkaDltNotifier dltNotifier;

    @InjectMocks
    private TranscriptRequestConsumer transcriptRequestConsumer;

    @Test
    @DisplayName("[onDlt는 정상 케이스에서 dltNotifier.notify를 videofeed-transcript 도메인으로 호출한다]")
    void onDlt_happyPath_notifiesDlt() {
        // given
        VideoSelectedMessage message = VideoSelectedMessage.of(1L);

        // when
        transcriptRequestConsumer.onDlt(message);

        // then
        verify(dltNotifier).notify(eq("videofeed-transcript"), eq(VideoFeedTopics.VIDEO_SELECTED), anyString());
    }

    @Test
    @DisplayName("[onDlt는 dltNotifier.notify 자체가 실패해도 예외를 전파하지 않는다 - 무한 재발행 루프 방지선]")
    void onDlt_dltNotifierThrows_doesNotPropagate() {
        // given
        VideoSelectedMessage message = VideoSelectedMessage.of(1L);
        willThrow(new RuntimeException("slack down")).given(dltNotifier).notify(any(), any(), any());

        // when & then
        assertThatCode(() -> transcriptRequestConsumer.onDlt(message)).doesNotThrowAnyException();
    }
}
