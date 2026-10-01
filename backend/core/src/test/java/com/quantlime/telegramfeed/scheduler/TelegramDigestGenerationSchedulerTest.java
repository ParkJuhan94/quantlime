package com.quantlime.telegramfeed.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.quantlime.telegramfeed.service.TelegramDigestGenerationFacade;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramDigestGenerationSchedulerTest {

    @Mock
    private TelegramDigestGenerationFacade facade;

    @InjectMocks
    private TelegramDigestGenerationScheduler scheduler;

    @Test
    @DisplayName("[실행하면 다이제스트 생성 파사드(채널별 이벤트 발행)에 위임하고, 결과가 있으면 예외 없이 끝난다]")
    void run_delegates() {
        // given
        given(facade.runAllExclusively()).willReturn(Optional.of(3));

        // when & then
        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
        verify(facade).runAllExclusively();
    }

    @Test
    @DisplayName("[다른 실행이 락을 쥐고 있어 결과가 비어 있으면 조용히 스킵한다]")
    void run_lockHeld_skips() {
        // given
        given(facade.runAllExclusively()).willReturn(Optional.empty());

        // when & then
        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
        verify(facade).runAllExclusively();
    }

    @Test
    @DisplayName("[위임 대상이 예외를 던져도 스케줄러 스레드로 전파하지 않는다]")
    void run_failure_isNotPropagated() {
        // given
        given(facade.runAllExclusively()).willThrow(new RuntimeException("boom"));

        // when & then
        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
    }
}
