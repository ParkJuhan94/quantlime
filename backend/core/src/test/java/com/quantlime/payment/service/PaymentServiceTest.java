package com.quantlime.payment.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.infra.tosspayments.dto.TossBillingKeyResponse;
import com.quantlime.infra.tosspayments.dto.TossPaymentApprovalResponse;
import com.quantlime.infra.tosspayments.exception.TossPaymentsErrorCode;
import com.quantlime.payment.implement.BillingProcessor;
import com.quantlime.payment.implement.PaymentAppender;
import com.quantlime.payment.implement.PaymentNotifier;
import com.quantlime.payment.implement.PaymentReader;
import com.quantlime.subscription.SubscriptionFixture;
import com.quantlime.subscription.SubscriptionPlanFixture;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.domain.SubscriptionStatus;
import com.quantlime.subscription.implement.SubscriptionReader;
import com.quantlime.subscription.service.SubscriptionPlanService;
import com.quantlime.subscription.service.SubscriptionService;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 2026-09-28 구현 레이어 분리 이후: 이 테스트는 PaymentService의 오케스트레이션
 * (무엇을 어떤 순서로 호출하는가)만 검증한다. Toss HTTP 상태 코드 판별(카드 거절
 * vs 429 vs 일시 장애) 같은 실제 분류 로직은 {@link BillingProcessor}로
 * 옮겨졌고, 그 로직 자체의 정확성은 {@code BillingProcessorTest}가 검증한다 -
 * 여기서는 billingProcessor.isBusinessDecline(...)의 결과값(true/false)만
 * 목킹해 PaymentService가 그 결과에 따라 올바른 분기를 타는지만 본다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private SubscriptionPlanService subscriptionPlanService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private SubscriptionReader subscriptionReader;

    @Mock
    private PaymentReader paymentReader;

    @Mock
    private PaymentAppender paymentAppender;

    @Mock
    private BillingProcessor billingProcessor;

    @Mock
    private PaymentNotifier paymentNotifier;

    @Mock
    private RedisLockService redisLockService;

    @InjectMocks
    private PaymentService paymentService;

    private final User user = UserFixture.createUser();
    private final SubscriptionPlan plan = SubscriptionPlanFixture.createPlan();
    private final Long userId = 1L;
    private final String lockKey = "subscription:subscribe-lock:" + userId;

    // 카드 거절이든 일시 장애든 원인은 이제 PaymentService 입장에서 무의미하다 -
    // billingProcessor.isBusinessDecline(...)의 반환값만 분기 기준이므로,
    // 어떤 cause를 달든 상관없는 자리표시자 예외를 하나만 둔다.
    private ExternalApiException tossChargeException() {
        return new ExternalApiException(TossPaymentsErrorCode.PAYMENT_CHARGE_FAILED);
    }

    // 최초 구독 흐름은 결제 전에 userId별 분산락을 잡는다 - RedisLockService를
    // 목킹해 "락을 획득했다"고 가정하고 실제 task(Supplier)를 그대로 실행시킨다.
    @SuppressWarnings("unchecked")
    private void givenLockAcquired() {
        given(redisLockService.runExclusively(eq(lockKey), any(Duration.class), any()))
            .willAnswer(invocation -> {
                Supplier<Object> task = invocation.getArgument(2);
                return Optional.of(task.get());
            });
    }

    private void givenLockNotAcquired() {
        given(redisLockService.runExclusively(eq(lockKey), any(Duration.class), any()))
            .willReturn(Optional.empty());
    }

    @Test
    @DisplayName("[빌링키 발급과 첫 결제가 모두 성공하면 구독을 시작하고 결제 이력을 남긴다]")
    void issueBillingKeyAndSubscribe_success_activatesSubscription() {
        // given
        givenLockAcquired();
        given(subscriptionPlanService.getByCode("PLAN_3M")).willReturn(plan);
        given(subscriptionReader.findByUserId(userId)).willReturn(Optional.empty());
        given(billingProcessor.issueBillingKey(anyString(), eq("auth-key")))
            .willReturn(new TossBillingKeyResponse("bk-1", "customer-1", "국민", "1234", "now"));
        given(billingProcessor.charge(
            eq("bk-1"), anyString(), anyString(), anyString(), eq(plan.getPriceWon()), eq(0)))
            .willReturn(new TossPaymentApprovalResponse("pk-1", "order-1", "구독", "DONE", plan.getPriceWon(), "카드", "now"));
        Subscription activated = SubscriptionFixture.createSubscription(user, plan);
        given(subscriptionService.activateOrResubscribe(userId, plan, "bk-1", 0)).willReturn(activated);

        // when
        Subscription result = paymentService.issueBillingKeyAndSubscribe(userId, "auth-key", "PLAN_3M", 0);

        // then
        assertThat(result).isEqualTo(activated);
        verify(paymentAppender).appendSuccess(
            eq(activated.getUser()), eq(activated), anyString(), eq(plan.getPriceWon()), eq(0),
            eq("pk-1"), eq(false));
        verify(paymentNotifier).notifySubscriptionStarted(userId, plan.getName());
    }

    @Test
    @DisplayName("[이미 구독중인 사용자가 다시 결제를 시도하면 카드사 호출 없이 400을 던진다]")
    void issueBillingKeyAndSubscribe_alreadyActive_throwsBeforeCallingToss() {
        // given
        givenLockAcquired();
        Subscription activeSubscription = SubscriptionFixture.createSubscription(user, plan);
        given(subscriptionPlanService.getByCode("PLAN_3M")).willReturn(plan);
        given(subscriptionReader.findByUserId(userId)).willReturn(Optional.of(activeSubscription));

        // when & then
        assertThatThrownBy(() ->
            paymentService.issueBillingKeyAndSubscribe(userId, "auth-key", "PLAN_3M", 0))
            .isInstanceOf(ValidationException.class);
        verify(billingProcessor, never()).issueBillingKey(anyString(), anyString());
    }

    @Test
    @DisplayName("[같은 사용자의 구독 요청이 이미 진행 중이면(락 미획득) 결제 전에 즉시 거절한다]")
    void issueBillingKeyAndSubscribe_lockNotAcquired_throwsWithoutCallingToss() {
        // given: 다른 요청이 이미 락을 선점(runExclusively가 빈 Optional 반환)
        givenLockNotAcquired();

        // when & then
        assertThatThrownBy(() ->
            paymentService.issueBillingKeyAndSubscribe(userId, "auth-key", "PLAN_3M", 0))
            .isInstanceOf(ValidationException.class);
        // 외부 결제는 물론 계획/조회조차 하지 않는다 - 락을 못 잡으면 task 자체가 실행되지 않는다.
        verify(subscriptionPlanService, never()).getByCode(anyString());
        verify(billingProcessor, never()).issueBillingKey(anyString(), anyString());
    }

    @Test
    @DisplayName("[할부 개월이 유효 범위를 벗어나면 카드사 호출 없이 400을 던진다]")
    void issueBillingKeyAndSubscribe_invalidInstallmentMonths_throwsBeforeCallingToss() {
        // when & then
        assertThatThrownBy(() ->
            paymentService.issueBillingKeyAndSubscribe(userId, "auth-key", "PLAN_3M", 1))
            .isInstanceOf(ValidationException.class);
        // 락 획득 시도 자체를 하지 않는다 - 유효성 검증이 락보다 먼저다.
        verify(redisLockService, never()).runExclusively(anyString(), any(Duration.class), any());
        verify(billingProcessor, never()).issueBillingKey(anyString(), anyString());
    }

    @Test
    @DisplayName("[빌링키 발급 후 첫 결제가 거절되면 구독을 만들지 않고 예외를 전파한다]")
    void issueBillingKeyAndSubscribe_chargeFails_doesNotPersistAnything() {
        // given
        givenLockAcquired();
        given(subscriptionPlanService.getByCode("PLAN_3M")).willReturn(plan);
        given(subscriptionReader.findByUserId(userId)).willReturn(Optional.empty());
        given(billingProcessor.issueBillingKey(anyString(), anyString()))
            .willReturn(new TossBillingKeyResponse("bk-1", "customer-1", "국민", "1234", "now"));
        given(billingProcessor.charge(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willThrow(tossChargeException());

        // when & then
        assertThatThrownBy(() ->
            paymentService.issueBillingKeyAndSubscribe(userId, "auth-key", "PLAN_3M", 0))
            .isInstanceOf(ExternalApiException.class);
        verify(subscriptionService, never()).activateOrResubscribe(any(), any(), any(), anyInt());
        verify(paymentAppender, never()).appendSuccess(
            any(), any(), anyString(), anyInt(), anyInt(), anyString(), anyBoolean());
        verify(paymentNotifier, never()).notifySubscriptionStarted(any(), any());
    }

    @Test
    @DisplayName("[자동 갱신 결제가 성공하면 구독 기간을 연장하고 실패 카운트를 초기화한다]")
    void chargeRenewal_success_renewsSubscription() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        Long subscriptionId = 100L;
        given(subscriptionReader.getById(subscriptionId)).willReturn(subscription);
        given(billingProcessor.charge(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willReturn(new TossPaymentApprovalResponse("pk-2", "order-2", "구독", "DONE", plan.getPriceWon(), "카드", "now"));
        LocalDate periodEndBefore = subscription.getCurrentPeriodEnd();

        // when
        paymentService.chargeRenewal(subscriptionId);

        // then
        assertThat(subscription.getCurrentPeriodEnd()).isAfter(periodEndBefore);
        assertThat(subscription.getRenewalFailureCount()).isZero();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(paymentAppender).appendSuccess(
            eq(user), eq(subscription), anyString(), eq(plan.getPriceWon()),
            eq(subscription.getInstallmentMonths()), eq("pk-2"), eq(true));
    }

    @Test
    @DisplayName("[자동 갱신 결제가 업무적 거절로 실패하면 내일로 재시도를 예약하고 결제수단 확인을 알린다"
        + "(3회 미만)]")
    void chargeRenewal_businessDeclineBelowThreshold_schedulesRetryTomorrow() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        Long subscriptionId = 100L;
        given(subscriptionReader.getById(subscriptionId)).willReturn(subscription);
        given(billingProcessor.charge(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willThrow(tossChargeException());
        given(billingProcessor.isBusinessDecline(any(ExternalApiException.class))).willReturn(true);

        // when
        paymentService.chargeRenewal(subscriptionId);

        // then
        assertThat(subscription.getRenewalFailureCount()).isEqualTo(1);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getNextBillingAt()).isEqualTo(LocalDate.now().plusDays(1));
        verify(paymentAppender).appendFailure(
            eq(user), eq(subscription), anyString(), eq(plan.getPriceWon()),
            eq(subscription.getInstallmentMonths()), eq(true), anyString());
        verify(paymentNotifier).notifyRenewalRetryScheduled(user.getId());
    }

    @Test
    @DisplayName("[자동 갱신 업무적 거절 재시도를 3회 모두 소진하면 PAST_DUE로 전환하고 FCM으로 알린다]")
    void chargeRenewal_businessDeclineAtThreshold_marksPastDue() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        subscription.recordRenewalFailure();
        subscription.recordRenewalFailure();
        Long subscriptionId = 100L;
        given(subscriptionReader.getById(subscriptionId)).willReturn(subscription);
        given(billingProcessor.charge(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willThrow(tossChargeException());
        given(billingProcessor.isBusinessDecline(any(ExternalApiException.class))).willReturn(true);

        // when
        paymentService.chargeRenewal(subscriptionId);

        // then
        assertThat(subscription.getRenewalFailureCount()).isEqualTo(3);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        verify(paymentNotifier).notifyPastDue(user.getId());
    }

    @Test
    @DisplayName("[billingProcessor가 업무적 거절이 아니라고 판단하면(일시 장애·429 등) DB 재시도 "
        + "카운트를 건드리지 않고 예외를 그대로 던져 카프카 재시도(30s~270s)가 처리하게 한다 - "
        + "구체적인 HTTP 상태 코드 판별 자체는 BillingProcessorTest가 검증한다]")
    void chargeRenewal_notBusinessDecline_rethrowsForKafkaRetry() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        Long subscriptionId = 100L;
        given(subscriptionReader.getById(subscriptionId)).willReturn(subscription);
        ExternalApiException transientError = tossChargeException();
        given(billingProcessor.charge(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willThrow(transientError);
        given(billingProcessor.isBusinessDecline(any(ExternalApiException.class))).willReturn(false);

        // when & then
        assertThatThrownBy(() -> paymentService.chargeRenewal(subscriptionId))
            .isSameAs(transientError);
        assertThat(subscription.getRenewalFailureCount()).isZero();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(paymentAppender, never()).appendFailure(
            any(), any(), anyString(), anyInt(), anyInt(), anyBoolean(), anyString());
        // 카프카가 곧 재시도할 일시 장애는 사용자에게 알리지 않는다.
        verify(paymentNotifier, never()).notifyRenewalRetryScheduled(any());
        verify(paymentNotifier, never()).notifyPastDue(any());
    }

    @Test
    @DisplayName("[이미 성공 처리된 갱신 결제(같은 과금주기)는 토스를 다시 호출하지 않고 멱등하게 스킵한다]")
    void chargeRenewal_alreadyProcessed_skipsTossCall() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        Long subscriptionId = 100L;
        given(subscriptionReader.getById(subscriptionId)).willReturn(subscription);
        given(paymentReader.isAlreadyProcessed(anyString())).willReturn(true);

        // when
        paymentService.chargeRenewal(subscriptionId);

        // then
        verify(billingProcessor, never()).charge(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("[카프카 재시도 소진 후 handleRenewalRetriesExhausted는 업무적 거절과 동일한 "
        + "DB 컬럼 재시도 경로(내일 재시도/PAST_DUE)로 합류시킨다]")
    void handleRenewalRetriesExhausted_belowThreshold_schedulesRetryTomorrow() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        Long subscriptionId = 100L;
        given(subscriptionReader.getById(subscriptionId)).willReturn(subscription);

        // when
        paymentService.handleRenewalRetriesExhausted(subscriptionId, "connect timed out");

        // then
        assertThat(subscription.getRenewalFailureCount()).isEqualTo(1);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getNextBillingAt()).isEqualTo(LocalDate.now().plusDays(1));
    }

    @Test
    @DisplayName("[카프카 재시도 소진(DLT)이 3회째 도달이면 PAST_DUE로 전환한다]")
    void handleRenewalRetriesExhausted_atThreshold_marksPastDue() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        subscription.recordRenewalFailure();
        subscription.recordRenewalFailure();
        Long subscriptionId = 100L;
        given(subscriptionReader.getById(subscriptionId)).willReturn(subscription);

        // when
        paymentService.handleRenewalRetriesExhausted(subscriptionId, "connect timed out");

        // then
        assertThat(subscription.getRenewalFailureCount()).isEqualTo(3);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        verify(paymentNotifier).notifyPastDue(user.getId());
    }

    @Test
    @DisplayName("[웹훅 서명이 유효하면 예외 없이 통과한다]")
    void handleWebhook_validSignature_doesNotThrow() {
        // given
        given(billingProcessor.verifyWebhookSignature("payload", "signature")).willReturn(true);

        // when & then
        paymentService.handleWebhook("payload", "signature");
    }

    @Test
    @DisplayName("[웹훅 서명이 유효하지 않으면 400을 던진다]")
    void handleWebhook_invalidSignature_throwsValidationException() {
        // given
        given(billingProcessor.verifyWebhookSignature("payload", "bad-signature")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> paymentService.handleWebhook("payload", "bad-signature"))
            .isInstanceOf(ValidationException.class);
    }
}
