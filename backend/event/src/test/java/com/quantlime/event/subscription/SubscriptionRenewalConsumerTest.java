package com.quantlime.event.subscription;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.quantlime.event.observability.KafkaDltNotifier;
import com.quantlime.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code @DltHandler}는 절대 예외를 던지면 안 된다는 불변식의 회귀 테스트.
 * 이 클래스는 2026-09-30에 실제로 이 불변식을 어겨 무한 재발행 루프를 낸
 * 장본인(exceptionMessage 헤더 필수 선언)이었고, 헤더는 고쳤지만 다른
 * 5개 핸들러와 달리 try/catch 방어는 이번 테스트 보강 세션까지 누락돼
 * 있었다 - handleRenewalRetriesExhausted가 실패하는 경로는 이 테스트가
 * 추가되기 전까지 무방비였다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SubscriptionRenewalConsumerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private KafkaDltNotifier dltNotifier;

    @InjectMocks
    private SubscriptionRenewalConsumer subscriptionRenewalConsumer;

    @Test
    @DisplayName("[onDlt는 exceptionMessage 헤더가 있으면 그 사유로 재시도 소진 처리를 위임한다]")
    void onDlt_headerPresent_usesHeaderAsReason() {
        // given
        SubscriptionRenewalDueMessage message = SubscriptionRenewalDueMessage.of(1L);

        // when
        subscriptionRenewalConsumer.onDlt(message, "connect timed out");

        // then
        verify(paymentService).handleRenewalRetriesExhausted(1L, "connect timed out");
        verify(dltNotifier).notify(eq("subscription-renewal"),
            eq(SubscriptionTopics.SUBSCRIPTION_RENEWAL_DUE), contains("connect timed out"));
    }

    @Test
    @DisplayName("[onDlt는 exceptionMessage 헤더가 없으면(required=false) 폴백 사유 문자열을 쓴다 - 실제 무한루프 사고의 근본 원인 회귀 테스트]")
    void onDlt_headerAbsent_usesFallbackReason() {
        // given
        SubscriptionRenewalDueMessage message = SubscriptionRenewalDueMessage.of(1L);

        // when
        subscriptionRenewalConsumer.onDlt(message, null);

        // then
        verify(paymentService).handleRenewalRetriesExhausted(1L, "사유 미상(DLT 예외 헤더 없음)");
    }

    @Test
    @DisplayName("[onDlt는 handleRenewalRetriesExhausted가 실패해도 예외를 전파하지 않는다]")
    void onDlt_paymentServiceThrows_doesNotPropagate() {
        // given
        SubscriptionRenewalDueMessage message = SubscriptionRenewalDueMessage.of(1L);
        willThrow(new RuntimeException("DB 제약 위반"))
            .given(paymentService).handleRenewalRetriesExhausted(any(), anyString());

        // when & then
        assertThatCode(() -> subscriptionRenewalConsumer.onDlt(message, "일시 장애"))
            .doesNotThrowAnyException();
    }
}
