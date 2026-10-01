package com.quantlime.subscription.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.subscription.event.SubscriptionRenewalDueEvent;
import com.quantlime.subscription.service.SubscriptionService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SubscriptionRenewalSchedulerTest {

    @Mock
    private RedisLockService redisLockService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SubscriptionRenewalScheduler scheduler;

    @SuppressWarnings("unchecked")
    private void lockAcquired() {
        given(redisLockService.runExclusively(eq("lock:subscription-renewal"), eq(Duration.ofMinutes(30)), any(Supplier.class)))
            .willAnswer(invocation -> Optional.of(((Supplier<Boolean>) invocation.getArgument(2)).get()));
    }

    @Test
    @DisplayName("[락을 잡으면 갱신 대상 구독마다 SubscriptionRenewalDueEvent를 발행하고, 그 뒤에 만료 처리를 수행한다]")
    void renewDueSubscriptions_publishesEventPerSubscription_thenExpiresLapsed() {
        // given
        lockAcquired();
        given(subscriptionService.findSubscriptionIdsDueForRenewal()).willReturn(List.of(1L, 2L, 3L));

        // when
        scheduler.renewDueSubscriptions();

        // then
        InOrder order = inOrder(eventPublisher, subscriptionService);
        order.verify(eventPublisher).publishEvent(new SubscriptionRenewalDueEvent(1L));
        order.verify(eventPublisher).publishEvent(new SubscriptionRenewalDueEvent(2L));
        order.verify(eventPublisher).publishEvent(new SubscriptionRenewalDueEvent(3L));
        order.verify(subscriptionService).expireLapsedSubscriptions();
    }

    @Test
    @DisplayName("[갱신 대상이 없어도 해지된 구독 만료 처리는 수행한다]")
    void renewDueSubscriptions_noDueSubscriptions_stillExpiresLapsed() {
        // given
        lockAcquired();
        given(subscriptionService.findSubscriptionIdsDueForRenewal()).willReturn(List.of());

        // when
        scheduler.renewDueSubscriptions();

        // then
        verifyNoInteractions(eventPublisher);
        verify(subscriptionService).expireLapsedSubscriptions();
    }

    @Test
    @DisplayName("[다른 인스턴스가 락을 쥐고 있으면 이중 결제를 막기 위해 이벤트 발행/만료 처리를 전혀 하지 않는다]")
    @SuppressWarnings("unchecked")
    void renewDueSubscriptions_lockHeld_skipsEverything() {
        // given
        given(redisLockService.runExclusively(eq("lock:subscription-renewal"), any(Duration.class), any(Supplier.class)))
            .willReturn(Optional.empty());

        // when
        scheduler.renewDueSubscriptions();

        // then
        verify(subscriptionService, never()).findSubscriptionIdsDueForRenewal();
        verify(subscriptionService, never()).expireLapsedSubscriptions();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("[대상 조회가 실패해도 예외가 전파되지 않는다(스케줄러 스레드 보호)]")
    void renewDueSubscriptions_queryFails_doesNotPropagate() {
        // given
        lockAcquired();
        willThrow(new RuntimeException("db down")).given(subscriptionService).findSubscriptionIdsDueForRenewal();

        // when & then
        assertThatCode(() -> scheduler.renewDueSubscriptions()).doesNotThrowAnyException();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("[만료 처리가 실패해도 예외가 전파되지 않고, 이미 발행한 갱신 이벤트에는 영향이 없다]")
    void renewDueSubscriptions_expireFails_doesNotPropagate() {
        // given
        lockAcquired();
        given(subscriptionService.findSubscriptionIdsDueForRenewal()).willReturn(List.of(7L));
        willThrow(new RuntimeException("boom")).given(subscriptionService).expireLapsedSubscriptions();

        // when & then
        assertThatCode(() -> scheduler.renewDueSubscriptions()).doesNotThrowAnyException();
        verify(eventPublisher).publishEvent(new SubscriptionRenewalDueEvent(7L));
    }
}
