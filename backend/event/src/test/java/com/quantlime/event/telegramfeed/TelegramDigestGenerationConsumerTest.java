package com.quantlime.event.telegramfeed;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.quantlime.event.observability.KafkaDltNotifier;
import com.quantlime.telegramfeed.service.TelegramDigestGenerationFacade;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@code @DltHandler} 무한 재발행 루프 방지 불변식의 회귀 테스트. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramDigestGenerationConsumerTest {

    @Mock
    private TelegramDigestGenerationFacade telegramDigestGenerationFacade;

    @Mock
    private KafkaDltNotifier dltNotifier;

    @InjectMocks
    private TelegramDigestGenerationConsumer telegramDigestGenerationConsumer;

    @Test
    @DisplayName("[onDlt는 exceptionMessage 헤더가 있으면 그 사유를 알림에 포함한다]")
    void onDlt_headerPresent_includesReasonInNotification() {
        // given
        TelegramDigestGenerationRequestedMessage message =
            TelegramDigestGenerationRequestedMessage.of(1L, LocalDate.now());

        // when
        telegramDigestGenerationConsumer.onDlt(message, "Gemini 쿼터 초과");

        // then
        verify(dltNotifier).notify(eq("telegram-digest"),
            eq(TelegramFeedTopics.TELEGRAM_DIGEST_GENERATION_REQUESTED), contains("Gemini 쿼터 초과"));
    }

    @Test
    @DisplayName("[onDlt는 exceptionMessage 헤더가 없으면(required=false) 폴백 사유 문자열을 쓴다]")
    void onDlt_headerAbsent_usesFallbackReason() {
        // given
        TelegramDigestGenerationRequestedMessage message =
            TelegramDigestGenerationRequestedMessage.of(1L, LocalDate.now());

        // when
        telegramDigestGenerationConsumer.onDlt(message, null);

        // then
        verify(dltNotifier).notify(eq("telegram-digest"),
            eq(TelegramFeedTopics.TELEGRAM_DIGEST_GENERATION_REQUESTED), contains("사유 미상(DLT 예외 헤더 없음)"));
    }

    @Test
    @DisplayName("[onDlt는 dltNotifier.notify 자체가 실패해도 예외를 전파하지 않는다]")
    void onDlt_dltNotifierThrows_doesNotPropagate() {
        // given
        TelegramDigestGenerationRequestedMessage message =
            TelegramDigestGenerationRequestedMessage.of(1L, LocalDate.now());
        willThrow(new RuntimeException("slack down")).given(dltNotifier).notify(any(), any(), any());

        // when & then
        assertThatCode(() -> telegramDigestGenerationConsumer.onDlt(message, "일시 장애"))
            .doesNotThrowAnyException();
    }
}
