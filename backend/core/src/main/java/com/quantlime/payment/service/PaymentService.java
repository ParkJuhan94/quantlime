package com.quantlime.payment.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.tosspayments.TossPaymentsApiClient;
import com.quantlime.infra.tosspayments.TossPaymentsProperties;
import com.quantlime.infra.tosspayments.TossWebhookVerifier;
import com.quantlime.infra.tosspayments.dto.TossBillingKeyResponse;
import com.quantlime.infra.tosspayments.dto.TossPaymentApprovalResponse;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.service.FcmPushService;
import com.quantlime.payment.domain.Payment;
import com.quantlime.payment.domain.PaymentStatus;
import com.quantlime.payment.exception.PaymentErrorCode;
import com.quantlime.payment.repository.PaymentRepository;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.domain.SubscriptionStatus;
import com.quantlime.subscription.exception.SubscriptionErrorCode;
import com.quantlime.subscription.repository.SubscriptionRepository;
import com.quantlime.subscription.service.SubscriptionPlanService;
import com.quantlime.subscription.service.SubscriptionService;
import com.quantlime.user.domain.User;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String CUSTOMER_KEY_PREFIX = "quantlime-user-";
    private static final String ORDER_NAME = "QuantLime 프리미엄 구독";
    private static final int MAX_RENEWAL_RETRY = 3;

    // 최초 구독(카드 등록+첫 결제)은 트랜잭션 밖에서 외부 결제를 부르므로
    // DB 유니크 제약만으로는 동시 요청의 이중 결제를 막지 못한다(둘 다
    // ACTIVE 체크를 통과하고 둘 다 결제에 성공한 뒤 두 번째 save만 제약
    // 위반으로 실패 → 결제는 됐는데 구독 row는 없는 불일치). userId별
    // Redis 락으로 결제 흐름 자체를 직렬화해, 결제가 최대 1회만 일어나게
    // 한다. TTL은 결제 왕복이 끝나기 전에 풀리지 않도록 넉넉히 잡되(30초),
    // 프로세스가 죽어 finally가 못 돌아도 자동 만료되게 한다.
    // Redis 장애 시 예외를 흡수하는 폴백은 두지 않는다 - 이 락이 막으려는
    // 건 이중 결제라, 실패를 조용히 넘기면 락이 아예 없는 것과 같아진다.
    // Redis가 죽으면 결제 흐름도 fail-closed로 막히는 게 맞는 동작이다
    // (2026-08-17, PriceCacheStore와 다른 판단 - docs/00-sre/SRE.md "캐시" 절 참고).
    // 이 락 자체의 알려진 한계(소유권 미검증 - RedisLockService의 UUID 토큰
    // 방식과 다름)는 결제 도메인 백로그로 별도 관리한다.
    private static final String SUBSCRIBE_LOCK_KEY_PREFIX = "subscription:subscribe-lock:";
    private static final Duration SUBSCRIBE_LOCK_TTL = Duration.ofSeconds(30);

    private final SubscriptionPlanService subscriptionPlanService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final TossPaymentsApiClient tossPaymentsApiClient;
    private final TossWebhookVerifier tossWebhookVerifier;
    private final TossPaymentsProperties tossPaymentsProperties;
    private final StringRedisTemplate redisTemplate;
    private final FcmPushService fcmPushService;

    // 카드 등록(빌링키 발급) 위젯 성공 콜백에서 호출한다 - 빌링키 발급과
    // 즉시 첫 결제를 한 번에 처리한다. Toss API 호출 자체는 트랜잭션
    // 밖에서 이뤄지고(외부 호출은 롤백 불가), 성공한 결과만
    // SubscriptionService.activateOrResubscribe의 트랜잭션 안에서 저장한다
    // (같은 클래스 안에서 @Transactional 메서드를 this로 호출하면 Spring
    // AOP 프록시를 안 거쳐 트랜잭션이 무시되므로, 저장 책임은 반드시
    // 다른 빈(SubscriptionService)에 둬야 한다).
    public Subscription issueBillingKeyAndSubscribe(
        Long userId, String authKey, String planCode, int installmentMonths) {
        validateInstallmentMonths(installmentMonths);

        // 락 획득 실패 = 같은 사용자의 다른 구독 요청이 이미 결제 중이라는
        // 뜻 - 외부 결제를 부르기 전에 즉시 거절해 이중 결제를 원천 차단한다.
        String lockKey = SUBSCRIBE_LOCK_KEY_PREFIX + userId;
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", SUBSCRIBE_LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.warn("구독 처리 중복 요청 차단(락 미획득): userId={}, planCode={}", userId, planCode);
            throw new ValidationException(SubscriptionErrorCode.SUBSCRIPTION_IN_PROGRESS);
        }

        // 락을 획득한 뒤에만 finally에서 삭제한다 - 획득 실패 시(위에서 이미
        // return) 여기에 오지 않으므로, 남의 락을 지우는 일은 없다.
        try {
            SubscriptionPlan plan = subscriptionPlanService.getByCode(planCode);
            SubscriptionStatus existingStatus = subscriptionRepository.findByUser_Id(userId)
                .map(Subscription::getStatus)
                .orElse(null);
            if (existingStatus == SubscriptionStatus.ACTIVE) {
                throw new ValidationException(SubscriptionErrorCode.ALREADY_SUBSCRIBED);
            }

            String customerKey = toCustomerKey(userId);
            TossBillingKeyResponse billingKeyResponse =
                tossPaymentsApiClient.issueBillingKey(customerKey, authKey);

            String orderId = generateOrderId();
            TossPaymentApprovalResponse approval;
            try {
                approval = tossPaymentsApiClient.chargeWithBillingKey(
                    billingKeyResponse.billingKey(), customerKey, orderId, ORDER_NAME,
                    plan.getPriceWon(), installmentMonths);
            } catch (ExternalApiException e) {
                // 카드 등록은 됐지만 첫 결제가 거절된 경우 - 아직 구독이
                // 만들어지지 않아(또는 갱신되지 않아) 남길 이력이 없다. 실패
                // 사실만 로그로 남기고 그대로 전파해 컨트롤러가 사용자에게
                // "다시 시도해주세요"를 보여줄 수 있게 한다.
                log.warn("구독 최초 결제 실패: userId={}, planCode={}, orderId={}, error={}",
                    userId, planCode, orderId, e.getMessage());
                throw e;
            }

            Subscription subscription = subscriptionService.activateOrResubscribe(
                userId, plan, billingKeyResponse.billingKey(), installmentMonths);

            paymentRepository.save(Payment.success(
                subscription.getUser(), subscription, orderId, plan.getPriceWon(), installmentMonths,
                approval.paymentKey(), false));

            log.info("구독 시작 완료: userId={}, planCode={}, orderId={}", userId, planCode, orderId);
            fcmPushService.sendToUser(userId, NotificationType.PAYMENT_SUCCESS,
                "구독이 시작되었습니다", plan.getName() + " 플랜 결제가 완료됐어요.", "/subscribe");
            return subscription;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    // Kafka 컨슈머(SubscriptionRenewalConsumer, event 모듈)가 건별로
    // 호출한다(2026-09-24, 카프카 다도메인 확장 Phase 2 - 예전에는
    // 스케줄러가 직접 호출했다). orderId를 결정론적으로 만들고(같은 과금
    // 주기의 재시도는 항상 같은 값) 사전에 이미 성공한 결제인지 먼저
    // 확인하는 이유 - 토스 결제승인 API가 orderId 재사용 시 자동으로
    // 중복을 막아주는지 공식 문서에 명시돼 있지 않아(2026-09-24 조사),
    // 우리 쪽에서 직접 멱등성을 보장해야 한다. payment.order_id의 DB
    // 유니크 제약(uk_payment_order_id)이 그 마지막 안전망이다.
    //
    // <p>실패 시 예외 처리를 두 갈래로 나눈다(2026-09-24 확정) - 카드 거절
    // 같은 업무적 거절(HTTP 4xx)은 몇 초~몇 분 뒤 재시도해도 결과가 똑같으므로
    // 카프카 재시도로 넘기지 않고 기존 DB 컬럼 재시도(내일 재시도/PAST_DUE)
    // 경로로 즉시 처리한다. 반대로 타임아웃·5xx 같은 일시 장애로 추정되는
    // 경우만 예외를 다시 던져 @RetryableTopic(30s→90s→270s)이 받게 한다 -
    // 재시도가 전부 소진되면 SubscriptionRenewalConsumer의 DLT 핸들러가
    // handleRenewalRetriesExhausted()로 같은 DB 컬럼 경로에 합류시킨다.
    @Transactional
    public void chargeRenewal(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException(SubscriptionErrorCode.NOT_FOUND_SUBSCRIPTION));
        User user = subscription.getUser();
        SubscriptionPlan plan = subscription.getPlan();
        String customerKey = toCustomerKey(user.getId());
        String orderId = renewalOrderId(subscriptionId, subscription.getCurrentPeriodEnd());

        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.DONE)) {
            log.info("이미 처리된 구독 갱신 결제, 스킵(멱등): subscriptionId={}, orderId={}",
                subscriptionId, orderId);
            return;
        }

        try {
            TossPaymentApprovalResponse approval = tossPaymentsApiClient.chargeWithBillingKey(
                subscription.getBillingKey(), customerKey, orderId, ORDER_NAME,
                plan.getPriceWon(), subscription.getInstallmentMonths());
            paymentRepository.save(Payment.success(
                user, subscription, orderId, plan.getPriceWon(),
                subscription.getInstallmentMonths(), approval.paymentKey(), true));
            subscription.renew();
            log.info("구독 자동 갱신 결제 성공: userId={}, subscriptionId={}, orderId={}",
                user.getId(), subscriptionId, orderId);
        } catch (ExternalApiException e) {
            if (isBusinessDecline(e)) {
                paymentRepository.save(Payment.failure(
                    user, subscription, orderId, plan.getPriceWon(),
                    subscription.getInstallmentMonths(), true, e.getMessage()));
                handleRenewalFailure(subscription, e.getMessage());
            } else {
                log.warn("구독 자동 갱신 일시 실패(카프카 재시도 예정): "
                        + "userId={}, subscriptionId={}, orderId={}, error={}",
                    user.getId(), subscriptionId, orderId, e.getMessage());
                throw e;
            }
        }
    }

    /**
     * 카프카 재시도(최대 6.5분)가 전부 소진된 뒤 {@code SubscriptionRenewalConsumer}의
     * {@code @DltHandler}가 호출한다 - 일시 장애로 시작했더라도 그 시간
     * 안에 회복 못 했으면 기존 DB 컬럼 재시도(내일 재시도/PAST_DUE) 경로로
     * 합류시켜, 이 구독의 재시도 상태가 두 메커니즘 중 하나로만 관리되게 한다.
     */
    @Transactional
    public void handleRenewalRetriesExhausted(Long subscriptionId, String errorMessage) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException(SubscriptionErrorCode.NOT_FOUND_SUBSCRIPTION));
        handleRenewalFailure(subscription, errorMessage);
    }

    private void handleRenewalFailure(Subscription subscription, String errorMessage) {
        Long userId = subscription.getUser().getId();
        Long subscriptionId = subscription.getId();
        subscription.recordRenewalFailure();
        if (subscription.getRenewalFailureCount() >= MAX_RENEWAL_RETRY) {
            subscription.markPastDue();
            log.warn("구독 자동 갱신 최종 실패(재시도 소진), PAST_DUE 전환: "
                    + "userId={}, subscriptionId={}, error={}",
                userId, subscriptionId, errorMessage);
            fcmPushService.sendToUser(userId, NotificationType.PAYMENT_FAILED,
                "결제에 실패했어요", "카드 결제가 계속 실패해 구독이 일시중지됐어요. 결제수단을 확인해주세요.",
                "/subscribe");
        } else {
            subscription.scheduleRenewalRetry(LocalDate.now().plusDays(1));
            log.warn("구독 자동 갱신 결제 실패, 내일 재시도: "
                    + "userId={}, subscriptionId={}, 시도횟수={}, error={}",
                userId, subscriptionId, subscription.getRenewalFailureCount(), errorMessage);
            // 아직 ACTIVE(프리미엄 유지)인 이 시점에 알려야 끊기기 전에
            // 결제수단을 바꿀 수 있다. 일시 장애(타임아웃/5xx)는 카프카가 먼저
            // 재시도하고 그게 다 실패해야 여기 오므로 알림이 폭주하진 않는다.
            fcmPushService.sendToUser(userId, NotificationType.PAYMENT_FAILED,
                "결제에 실패했어요", "내일 다시 결제를 시도해요. 구독은 유지 중이니 결제수단을 확인해주세요.",
                "/subscribe");
        }
    }

    /**
     * HTTP 4xx(카드 거절 등 업무적 거절)인지 판단한다 - 5xx/타임아웃/연결
     * 실패와 달리 몇 초~몇 분 뒤 재시도해도 같은 결과가 나오므로 카프카
     * 재시도 대상에서 제외한다(클래스 상단 chargeRenewal 주석 참고).
     */
    private boolean isBusinessDecline(ExternalApiException e) {
        return e.getCause() instanceof HttpClientErrorException;
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentHistory(Long userId) {
        return paymentRepository.findAllByUser_IdOrderByCreatedAtDesc(userId);
    }

    // 최소 구현: 서명 검증 + 수신 로깅까지. 카드 자동결제는 승인이
    // 동기(API 응답)로 오기 때문에 웹훅에 의존하는 상태 전이가 아직
    // 없다 - 실제 페이로드를 받아보며 이벤트 타입별 처리를 확장한다.
    public void handleWebhook(String payload, String signatureHeader) {
        boolean valid = tossWebhookVerifier.verify(
            payload, signatureHeader, tossPaymentsProperties.getWebhookSecret());
        if (!valid) {
            throw new ValidationException(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }
        log.info("토스페이먼츠 웹훅 수신: payload={}", payload);
    }

    private void validateInstallmentMonths(int installmentMonths) {
        boolean valid = installmentMonths == 0
            || (installmentMonths >= 2 && installmentMonths <= 12);
        if (!valid) {
            throw new ValidationException(SubscriptionErrorCode.INVALID_INSTALLMENT_MONTHS);
        }
    }

    // SubscriptionController가 카드 등록 위젯을 열기 전에 프론트에 customerKey를
    // 내려줘야 해서(GET /api/subscription/me) public으로 노출한다 - 이
    // 값과 실제 Toss 호출에 쓰는 값이 반드시 같아야 하므로 소스를 하나로 유지.
    public String toCustomerKey(Long userId) {
        return CUSTOMER_KEY_PREFIX + userId;
    }

    private String generateOrderId() {
        return "SUB-" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 구독 자동 갱신 전용 결정론적 orderId(2026-09-24) - 최초 결제
     * ({@code generateOrderId})와 달리 매번 새 값을 쓰지 않는다.
     * {@code currentPeriodEnd}는 이번 과금 주기의 갱신이 성공(renew())하기
     * 전까지는 값이 바뀌지 않으므로, 같은 과금 주기에 대한 재시도(카프카
     * 재시도든 다음날 DB 컬럼 재시도든)는 항상 같은 orderId를 만들어낸다 -
     * chargeRenewal의 사전 확인(existsByOrderIdAndStatus)이 이 값을 키로 쓴다.
     */
    private String renewalOrderId(Long subscriptionId, LocalDate currentPeriodEnd) {
        return "SUB-RENEWAL-" + subscriptionId + "-" + currentPeriodEnd;
    }
}
