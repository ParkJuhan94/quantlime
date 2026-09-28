package com.quantlime.payment.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.tosspayments.TossPaymentsApiClient;
import com.quantlime.infra.tosspayments.TossPaymentsProperties;
import com.quantlime.infra.tosspayments.TossWebhookVerifier;
import com.quantlime.infra.tosspayments.dto.TossBillingKeyResponse;
import com.quantlime.infra.tosspayments.dto.TossPaymentApprovalResponse;
import com.quantlime.infra.tosspayments.exception.TossPaymentsErrorCode;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.service.FcmPushService;
import com.quantlime.payment.domain.Payment;
import com.quantlime.payment.domain.PaymentStatus;
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
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
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

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private FcmPushService fcmPushService;

    @InjectMocks
    private PaymentService paymentService;

    private final User user = UserFixture.createUser();
    private final SubscriptionPlan plan = SubscriptionPlanFixture.createPlan();
    private final Long userId = 1L;
    private final String lockKey = "subscription:subscribe-lock:" + userId;

    // 카드 거절(HTTP 4xx)처럼 재시도해도 결과가 똑같은 "업무적 거절"을 흉내낸다 -
    // PaymentService.isBusinessDecline이 원인(cause)의 타입으로 판단하므로
    // 이 타입이어야 handleRenewalFailure(재시도 예약/PAST_DUE 전환) 경로를 탄다.
    private ExternalApiException businessDeclineException() {
        HttpClientErrorException decline = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null);
        return new ExternalApiException(TossPaymentsErrorCode.PAYMENT_CHARGE_FAILED, decline);
    }

    // 최초 구독 흐름은 결제 전에 userId별 Redis 락을 잡는다 - 락 흐름까지
    // 도달하는 테스트에서만 호출한다(할부 유효성 실패처럼 락 전에 던지는
    // 테스트에서 호출하면 strict stubbing이 unnecessary stubbing으로 걸린다).
    private void givenSubscribeLockAcquired() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(eq(lockKey), anyString(), any(Duration.class)))
            .willReturn(true);
    }

    @Test
    @DisplayName("[빌링키 발급과 첫 결제가 모두 성공하면 구독을 시작하고 결제 이력을 남긴다]")
    void issueBillingKeyAndSubscribe_success_activatesSubscription() {
        // given
        givenSubscribeLockAcquired();
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
        verify(redisTemplate).delete(lockKey);
        verify(fcmPushService).sendToUser(
            eq(userId), eq(NotificationType.PAYMENT_SUCCESS), anyString(), anyString(), eq("/subscribe"));
    }

    @Test
    @DisplayName("[이미 구독중인 사용자가 다시 결제를 시도하면 카드사 호출 없이 400을 던진다]")
    void issueBillingKeyAndSubscribe_alreadyActive_throwsBeforeCallingToss() {
        // given
        givenSubscribeLockAcquired();
        Subscription activeSubscription = SubscriptionFixture.createSubscription(user, plan);
        given(subscriptionPlanService.getByCode("PLAN_3M")).willReturn(plan);
        given(subscriptionRepository.findByUser_Id(userId)).willReturn(Optional.of(activeSubscription));

        // when & then
        assertThatThrownBy(() ->
            paymentService.issueBillingKeyAndSubscribe(userId, "auth-key", "PLAN_3M", 0))
            .isInstanceOf(ValidationException.class);
        verify(tossPaymentsApiClient, never()).issueBillingKey(anyString(), anyString());
        // 락을 획득한 요청이므로(ACTIVE로 거절돼도) 락은 반드시 풀려야 한다.
        verify(redisTemplate).delete(lockKey);
    }

    @Test
    @DisplayName("[같은 사용자의 구독 요청이 이미 진행 중이면(락 미획득) 결제 전에 즉시 거절한다]")
    void issueBillingKeyAndSubscribe_lockNotAcquired_throwsWithoutCallingToss() {
        // given: 다른 요청이 이미 락을 선점(setIfAbsent가 false 반환)
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(eq(lockKey), anyString(), any(Duration.class)))
            .willReturn(false);

        // when & then
        assertThatThrownBy(() ->
            paymentService.issueBillingKeyAndSubscribe(userId, "auth-key", "PLAN_3M", 0))
            .isInstanceOf(ValidationException.class);
        // 외부 결제는 물론 계획/조회조차 하지 않고, 남의 락을 지우지도 않는다.
        verify(subscriptionPlanService, never()).getByCode(anyString());
        verify(tossPaymentsApiClient, never()).issueBillingKey(anyString(), anyString());
        verify(redisTemplate, never()).delete(anyString());
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
        givenSubscribeLockAcquired();
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
        verify(fcmPushService, never()).sendToUser(any(), any(), any(), any(), any());
        // 결제 실패로 예외가 나도 finally에서 락은 반드시 풀린다.
        verify(redisTemplate).delete(lockKey);
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
    @DisplayName("[자동 갱신 결제가 카드 거절(업무적 거절)로 실패하면 내일로 재시도를 예약하고 결제수단 확인을 알린다(3회 미만)]")
    void chargeRenewal_businessDeclineBelowThreshold_schedulesRetryTomorrow() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        Long subscriptionId = 100L;
        given(subscriptionRepository.findById(subscriptionId)).willReturn(Optional.of(subscription));
        given(tossPaymentsApiClient.chargeWithBillingKey(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willThrow(businessDeclineException());

        // when
        paymentService.chargeRenewal(subscriptionId);

        // then
        assertThat(subscription.getRenewalFailureCount()).isEqualTo(1);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getNextBillingAt()).isEqualTo(LocalDate.now().plusDays(1));
        verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.FAILED));
        // 아직 ACTIVE라 결제수단을 바꿀 여지가 있다는 걸 알려야 하는 경로(내일 재시도) -
        // PaymentService.handleRenewalFailure의 else 분기.
        verify(fcmPushService).sendToUser(eq(user.getId()), eq(NotificationType.PAYMENT_FAILED),
            anyString(), contains("내일 다시"), eq("/subscribe"));
    }

    @Test
    @DisplayName("[자동 갱신 카드 거절 재시도를 3회 모두 소진하면 PAST_DUE로 전환하고 일시중지를 알린다]")
    void chargeRenewal_businessDeclineAtThreshold_marksPastDue() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        subscription.recordRenewalFailure();
        subscription.recordRenewalFailure();
        Long subscriptionId = 100L;
        given(subscriptionRepository.findById(subscriptionId)).willReturn(Optional.of(subscription));
        given(tossPaymentsApiClient.chargeWithBillingKey(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willThrow(businessDeclineException());

        // when
        paymentService.chargeRenewal(subscriptionId);

        // then
        assertThat(subscription.getRenewalFailureCount()).isEqualTo(3);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        verify(fcmPushService).sendToUser(eq(user.getId()), eq(NotificationType.PAYMENT_FAILED),
            anyString(), contains("일시중지"), eq("/subscribe"));
    }

    @Test
    @DisplayName("[이미 같은 과금 주기에 결제가 성공 처리돼 있으면(멱등) 토스를 다시 호출하지 않고 스킵한다]")
    void chargeRenewal_alreadyProcessed_skipsWithoutCallingToss() {
        // given: 카프카 재시도 또는 다음날 DB 재시도가 이미 성공한 결제와 같은
        // orderId(구독ID+currentPeriodEnd로 결정론적)로 다시 들어온 상황.
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        Long subscriptionId = 100L;
        given(subscriptionRepository.findById(subscriptionId)).willReturn(Optional.of(subscription));
        given(paymentRepository.existsByOrderIdAndStatus(anyString(), eq(PaymentStatus.DONE)))
            .willReturn(true);

        // when
        paymentService.chargeRenewal(subscriptionId);

        // then
        verify(tossPaymentsApiClient, never()).chargeWithBillingKey(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt());
        verify(paymentRepository, never()).save(any());
        assertThat(subscription.getRenewalFailureCount()).isZero();
    }

    @Test
    @DisplayName("[일시 장애(타임아웃/5xx 등)로 결제가 실패하면 상태를 건드리지 않고 예외를 다시 던진다(카프카 재시도 대상)]")
    void chargeRenewal_transientFailure_rethrowsWithoutUpdatingState() {
        // given: 카드 거절(HttpClientErrorException)이 아닌 원인 - 연결 실패/타임아웃 등
        // 재시도하면 결과가 달라질 수 있는 일시 장애를 흉내낸다.
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        Long subscriptionId = 100L;
        given(subscriptionRepository.findById(subscriptionId)).willReturn(Optional.of(subscription));
        given(tossPaymentsApiClient.chargeWithBillingKey(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willThrow(new ExternalApiException(
                TossPaymentsErrorCode.PAYMENT_CHARGE_FAILED, new RuntimeException("connect timed out")));

        // when & then
        assertThatThrownBy(() -> paymentService.chargeRenewal(subscriptionId))
            .isInstanceOf(ExternalApiException.class);
        // 일시 장애는 여기서 DB 컬럼 재시도로 흡수하지 않는다 - @RetryableTopic이
        // 재시도하고, 그마저 소진돼야 handleRenewalRetriesExhausted가 처리한다.
        verify(paymentRepository, never()).save(any());
        assertThat(subscription.getRenewalFailureCount()).isZero();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(fcmPushService, never()).sendToUser(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("[카프카 재시도 소진 후에도(DLT) 재시도 미만이면 내일로 재시도를 예약한다]")
    void handleRenewalRetriesExhausted_belowThreshold_schedulesRetryTomorrow() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        Long subscriptionId = 100L;
        given(subscriptionRepository.findById(subscriptionId)).willReturn(Optional.of(subscription));

        // when
        paymentService.handleRenewalRetriesExhausted(subscriptionId, "connect timed out");

        // then
        assertThat(subscription.getRenewalFailureCount()).isEqualTo(1);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getNextBillingAt()).isEqualTo(LocalDate.now().plusDays(1));
        verify(fcmPushService).sendToUser(
            eq(user.getId()), eq(NotificationType.PAYMENT_FAILED), anyString(), anyString(), eq("/subscribe"));
    }

    @Test
    @DisplayName("[카프카 재시도 소진(DLT)이 3회째 도달이면 PAST_DUE로 전환한다]")
    void handleRenewalRetriesExhausted_atThreshold_marksPastDue() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        subscription.recordRenewalFailure();
        subscription.recordRenewalFailure();
        Long subscriptionId = 100L;
        given(subscriptionRepository.findById(subscriptionId)).willReturn(Optional.of(subscription));

        // when
        paymentService.handleRenewalRetriesExhausted(subscriptionId, "connect timed out");

        // then
        assertThat(subscription.getRenewalFailureCount()).isEqualTo(3);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        verify(fcmPushService).sendToUser(
            eq(user.getId()), eq(NotificationType.PAYMENT_FAILED), anyString(), anyString(), eq("/subscribe"));
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
