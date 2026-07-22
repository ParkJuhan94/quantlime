package com.quantlime.payment.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.tosspayments.TossPaymentsApiClient;
import com.quantlime.infra.tosspayments.TossPaymentsProperties;
import com.quantlime.infra.tosspayments.TossWebhookVerifier;
import com.quantlime.infra.tosspayments.dto.TossBillingKeyResponse;
import com.quantlime.infra.tosspayments.dto.TossPaymentApprovalResponse;
import com.quantlime.infra.tosspayments.exception.TossPaymentsErrorCode;
import com.quantlime.payment.domain.Payment;
import com.quantlime.payment.repository.PaymentRepository;
import com.quantlime.subscription.SubscriptionFixture;
import com.quantlime.subscription.SubscriptionPlanFixture;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.domain.SubscriptionStatus;
import com.quantlime.subscription.repository.SubscriptionRepository;
import com.quantlime.subscription.service.SubscriptionPlanService;
import com.quantlime.subscription.service.SubscriptionService;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import java.time.LocalDate;
import java.util.Optional;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private SubscriptionPlanService subscriptionPlanService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TossPaymentsApiClient tossPaymentsApiClient;

    @Mock
    private TossWebhookVerifier tossWebhookVerifier;

    @Mock
    private TossPaymentsProperties tossPaymentsProperties;

    @InjectMocks
    private PaymentService paymentService;

    private final User user = UserFixture.createUser();
    private final SubscriptionPlan plan = SubscriptionPlanFixture.createPlan();
    private final Long userId = 1L;

    @Test
    @DisplayName("[빌링키 발급과 첫 결제가 모두 성공하면 구독을 시작하고 결제 이력을 남긴다]")
    void issueBillingKeyAndSubscribe_success_activatesSubscription() {
        // given
        given(subscriptionPlanService.getByCode("PLAN_3M")).willReturn(plan);
        given(subscriptionRepository.findByUser_Id(userId)).willReturn(Optional.empty());
        given(tossPaymentsApiClient.issueBillingKey(anyString(), eq("auth-key")))
            .willReturn(new TossBillingKeyResponse("bk-1", "customer-1", "국민", "1234", "now"));
        given(tossPaymentsApiClient.chargeWithBillingKey(
            eq("bk-1"), anyString(), anyString(), anyString(), eq(plan.getPriceWon()), eq(0)))
            .willReturn(new TossPaymentApprovalResponse("pk-1", "order-1", "구독", "DONE", plan.getPriceWon(), "카드", "now"));
        Subscription activated = SubscriptionFixture.createSubscription(user, plan);
        given(subscriptionService.activateOrResubscribe(userId, plan, "bk-1", 0)).willReturn(activated);

        // when
        Subscription result = paymentService.issueBillingKeyAndSubscribe(userId, "auth-key", "PLAN_3M", 0);

        // then
        assertThat(result).isEqualTo(activated);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("[이미 구독중인 사용자가 다시 결제를 시도하면 카드사 호출 없이 400을 던진다]")
    void issueBillingKeyAndSubscribe_alreadyActive_throwsBeforeCallingToss() {
        // given
        Subscription activeSubscription = SubscriptionFixture.createSubscription(user, plan);
        given(subscriptionPlanService.getByCode("PLAN_3M")).willReturn(plan);
        given(subscriptionRepository.findByUser_Id(userId)).willReturn(Optional.of(activeSubscription));

        // when & then
        assertThatThrownBy(() ->
            paymentService.issueBillingKeyAndSubscribe(userId, "auth-key", "PLAN_3M", 0))
            .isInstanceOf(ValidationException.class);
        verify(tossPaymentsApiClient, never()).issueBillingKey(anyString(), anyString());
    }

    @Test
    @DisplayName("[할부 개월이 유효 범위를 벗어나면 카드사 호출 없이 400을 던진다]")
    void issueBillingKeyAndSubscribe_invalidInstallmentMonths_throwsBeforeCallingToss() {
        // when & then
        assertThatThrownBy(() ->
            paymentService.issueBillingKeyAndSubscribe(userId, "auth-key", "PLAN_3M", 1))
            .isInstanceOf(ValidationException.class);
        verify(tossPaymentsApiClient, never()).issueBillingKey(anyString(), anyString());
    }

    @Test
    @DisplayName("[빌링키 발급 후 첫 결제가 거절되면 구독을 만들지 않고 예외를 전파한다]")
    void issueBillingKeyAndSubscribe_chargeFails_doesNotPersistAnything() {
        // given
        given(subscriptionPlanService.getByCode("PLAN_3M")).willReturn(plan);
        given(subscriptionRepository.findByUser_Id(userId)).willReturn(Optional.empty());
        given(tossPaymentsApiClient.issueBillingKey(anyString(), anyString()))
            .willReturn(new TossBillingKeyResponse("bk-1", "customer-1", "국민", "1234", "now"));
        given(tossPaymentsApiClient.chargeWithBillingKey(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willThrow(new ExternalApiException(TossPaymentsErrorCode.PAYMENT_CHARGE_FAILED));

        // when & then
        assertThatThrownBy(() ->
            paymentService.issueBillingKeyAndSubscribe(userId, "auth-key", "PLAN_3M", 0))
            .isInstanceOf(ExternalApiException.class);
        verify(subscriptionService, never()).activateOrResubscribe(any(), any(), any(), anyInt());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("[자동 갱신 결제가 성공하면 구독 기간을 연장하고 실패 카운트를 초기화한다]")
    void chargeRenewal_success_renewsSubscription() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        Long subscriptionId = 100L;
        given(subscriptionRepository.findById(subscriptionId)).willReturn(Optional.of(subscription));
        given(tossPaymentsApiClient.chargeWithBillingKey(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willReturn(new TossPaymentApprovalResponse("pk-2", "order-2", "구독", "DONE", plan.getPriceWon(), "카드", "now"));
        LocalDate periodEndBefore = subscription.getCurrentPeriodEnd();

        // when
        paymentService.chargeRenewal(subscriptionId);

        // then
        assertThat(subscription.getCurrentPeriodEnd()).isAfter(periodEndBefore);
        assertThat(subscription.getRenewalFailureCount()).isZero();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("[자동 갱신 결제가 실패하면 내일로 재시도를 예약한다(3회 미만)]")
    void chargeRenewal_failureBelowThreshold_schedulesRetryTomorrow() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        Long subscriptionId = 100L;
        given(subscriptionRepository.findById(subscriptionId)).willReturn(Optional.of(subscription));
        given(tossPaymentsApiClient.chargeWithBillingKey(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willThrow(new ExternalApiException(TossPaymentsErrorCode.PAYMENT_CHARGE_FAILED));

        // when
        paymentService.chargeRenewal(subscriptionId);

        // then
        assertThat(subscription.getRenewalFailureCount()).isEqualTo(1);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getNextBillingAt()).isEqualTo(LocalDate.now().plusDays(1));
    }

    @Test
    @DisplayName("[자동 갱신 재시도를 3회 모두 소진하면 PAST_DUE로 전환한다]")
    void chargeRenewal_failureAtThreshold_marksPastDue() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        subscription.recordRenewalFailure();
        subscription.recordRenewalFailure();
        Long subscriptionId = 100L;
        given(subscriptionRepository.findById(subscriptionId)).willReturn(Optional.of(subscription));
        given(tossPaymentsApiClient.chargeWithBillingKey(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willThrow(new ExternalApiException(TossPaymentsErrorCode.PAYMENT_CHARGE_FAILED));

        // when
        paymentService.chargeRenewal(subscriptionId);

        // then
        assertThat(subscription.getRenewalFailureCount()).isEqualTo(3);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    @DisplayName("[웹훅 서명이 유효하면 예외 없이 통과한다]")
    void handleWebhook_validSignature_doesNotThrow() {
        // given
        given(tossPaymentsProperties.getWebhookSecret()).willReturn("secret");
        given(tossWebhookVerifier.verify("payload", "signature", "secret")).willReturn(true);

        // when & then
        paymentService.handleWebhook("payload", "signature");
    }

    @Test
    @DisplayName("[웹훅 서명이 유효하지 않으면 400을 던진다]")
    void handleWebhook_invalidSignature_throwsValidationException() {
        // given
        given(tossPaymentsProperties.getWebhookSecret()).willReturn("secret");
        given(tossWebhookVerifier.verify("payload", "bad-signature", "secret")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> paymentService.handleWebhook("payload", "bad-signature"))
            .isInstanceOf(ValidationException.class);
    }
}
