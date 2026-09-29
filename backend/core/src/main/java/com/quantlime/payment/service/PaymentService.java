package com.quantlime.payment.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.infra.tosspayments.dto.TossBillingKeyResponse;
import com.quantlime.infra.tosspayments.dto.TossPaymentApprovalResponse;
import com.quantlime.payment.domain.Payment;
import com.quantlime.payment.event.PaymentWebhookReceivedEvent;
import com.quantlime.payment.exception.PaymentErrorCode;
import com.quantlime.payment.implement.BillingProcessor;
import com.quantlime.payment.implement.PaymentAppender;
import com.quantlime.payment.implement.PaymentNotifier;
import com.quantlime.payment.implement.PaymentReader;
import com.quantlime.payment.implement.PaymentWebhookDedupStore;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.domain.SubscriptionStatus;
import com.quantlime.subscription.exception.SubscriptionErrorCode;
import com.quantlime.subscription.implement.SubscriptionReader;
import com.quantlime.subscription.service.SubscriptionPlanService;
import com.quantlime.subscription.service.SubscriptionService;
import com.quantlime.user.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구독 결제 흐름을 오케스트레이션한다 - 조회/저장/외부 API 호출/알림 발송의
 * 상세 구현은 전부 {@code payment.implement}/{@code subscription.implement}
 * 패키지의 협력 객체(Reader/Appender/Processor/Notifier)에 위임하고, 이
 * 클래스는 "무엇을 어떤 순서로 하는가"라는 흐름만 남긴다(2026-09-28
 * 구현 레이어 분리 - 이전엔 Repository/infra 클라이언트를 직접 호출해
 * 흐름과 상세 구현이 뒤섞여 있었다).
 */
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
    // 분산락으로 결제 흐름 자체를 직렬화해, 결제가 최대 1회만 일어나게
    // 한다. TTL은 결제 왕복이 끝나기 전에 풀리지 않도록 넉넉히 잡되(30초),
    // 프로세스가 죽어도 자동 만료되게 한다.
    // RedisLockService는 Redis 장애 시 예외를 흡수하는 폴백을 두지 않는다 -
    // 이 락이 막으려는 건 이중 결제라, 실패를 조용히 넘기면 락이 아예
    // 없는 것과 같아진다. Redis가 죽으면 결제 흐름도 fail-closed로 막히는
    // 게 맞는 동작이다(2026-08-17, PriceCacheStore와 다른 판단 -
    // docs/00-sre/SRE.md "캐시" 절 참고).
    // 2026-09-28부로 소유권을 검증하지 않던 자체 구현(StringRedisTemplate
    // setIfAbsent+delete)을 걷어내고, UUID 토큰+Lua CAS로 TTL 만료 후에도
    // 남의 락을 지우지 않는 RedisLockService로 교체했다(SRE.md 결제 도메인
    // 백로그 #5 해결).
    private static final String SUBSCRIBE_LOCK_KEY_PREFIX = "subscription:subscribe-lock:";
    private static final Duration SUBSCRIBE_LOCK_TTL = Duration.ofSeconds(30);

    private final SubscriptionPlanService subscriptionPlanService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionReader subscriptionReader;
    private final PaymentReader paymentReader;
    private final PaymentAppender paymentAppender;
    private final BillingProcessor billingProcessor;
    private final PaymentNotifier paymentNotifier;
    private final RedisLockService redisLockService;
    private final PaymentWebhookDedupStore paymentWebhookDedupStore;
    private final ApplicationEventPublisher eventPublisher;

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

        String lockKey = SUBSCRIBE_LOCK_KEY_PREFIX + userId;
        return redisLockService.runExclusively(lockKey, SUBSCRIBE_LOCK_TTL, () -> {
            SubscriptionPlan plan = subscriptionPlanService.getByCode(planCode);
            SubscriptionStatus existingStatus = subscriptionReader.findByUserId(userId)
                .map(Subscription::getStatus)
                .orElse(null);
            if (existingStatus == SubscriptionStatus.ACTIVE) {
                throw new ValidationException(SubscriptionErrorCode.ALREADY_SUBSCRIBED);
            }

            String customerKey = toCustomerKey(userId);
            TossBillingKeyResponse billingKeyResponse =
                billingProcessor.issueBillingKey(customerKey, authKey);

            String orderId = generateOrderId();
            TossPaymentApprovalResponse approval;
            try {
                approval = billingProcessor.charge(
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

            paymentAppender.appendSuccess(
                subscription.getUser(), subscription, orderId, plan.getPriceWon(), installmentMonths,
                approval.paymentKey(), false);

            log.info("구독 시작 완료: userId={}, planCode={}, orderId={}", userId, planCode, orderId);
            paymentNotifier.notifySubscriptionStarted(userId, plan.getName());
            return subscription;
        }).orElseThrow(() -> {
            // 락 획득 실패 = 같은 사용자의 다른 구독 요청이 이미 결제 중이라는
            // 뜻 - 외부 결제를 부르기 전에 즉시 거절해 이중 결제를 원천 차단한다.
            log.warn("구독 처리 중복 요청 차단(락 미획득): userId={}, planCode={}", userId, planCode);
            return new ValidationException(SubscriptionErrorCode.SUBSCRIPTION_IN_PROGRESS);
        });
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
        Subscription subscription = subscriptionReader.getById(subscriptionId);
        User user = subscription.getUser();
        SubscriptionPlan plan = subscription.getPlan();
        String customerKey = toCustomerKey(user.getId());
        String orderId = renewalOrderId(subscriptionId, subscription.getCurrentPeriodEnd());

        if (paymentReader.isAlreadyProcessed(orderId)) {
            log.info("이미 처리된 구독 갱신 결제, 스킵(멱등): subscriptionId={}, orderId={}",
                subscriptionId, orderId);
            return;
        }

        try {
            TossPaymentApprovalResponse approval = billingProcessor.charge(
                subscription.getBillingKey(), customerKey, orderId, ORDER_NAME,
                plan.getPriceWon(), subscription.getInstallmentMonths());
            paymentAppender.appendSuccess(
                user, subscription, orderId, plan.getPriceWon(),
                subscription.getInstallmentMonths(), approval.paymentKey(), true);
            subscription.renew();
            log.info("구독 자동 갱신 결제 성공: userId={}, subscriptionId={}, orderId={}",
                user.getId(), subscriptionId, orderId);
        } catch (ExternalApiException e) {
            if (billingProcessor.isBusinessDecline(e)) {
                paymentAppender.appendFailure(
                    user, subscription, orderId, plan.getPriceWon(),
                    subscription.getInstallmentMonths(), true, e.getMessage());
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
        Subscription subscription = subscriptionReader.getById(subscriptionId);
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
            paymentNotifier.notifyPastDue(userId);
        } else {
            subscription.scheduleRenewalRetry(LocalDate.now().plusDays(1));
            log.warn("구독 자동 갱신 결제 실패, 내일 재시도: "
                    + "userId={}, subscriptionId={}, 시도횟수={}, error={}",
                userId, subscriptionId, subscription.getRenewalFailureCount(), errorMessage);
            // 아직 ACTIVE(프리미엄 유지)인 이 시점에 알려야 끊기기 전에
            // 결제수단을 바꿀 수 있다. 일시 장애(타임아웃/5xx)는 카프카가 먼저
            // 재시도하고 그게 다 실패해야 여기 오므로 알림이 폭주하진 않는다.
            paymentNotifier.notifyRenewalRetryScheduled(userId);
        }
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentHistory(Long userId) {
        return paymentReader.getHistory(userId);
    }

    // 서명 검증까지만 동기로 처리하고, 실제 처리는 카프카 컨슈머
    // (PaymentWebhookConsumer, event 모듈)에 위임한다(2026-09-30, 카프카
    // 다도메인 확장 Phase 3 - 이전엔 서명 검증+로깅을 컨트롤러 요청
    // 스레드에서 그대로 처리했다). 컨트롤러가 Toss에 200을 최대한 빨리
    // 돌려줘야 재전송을 피할 수 있고, 향후 이벤트 타입별 처리가 무거워져도
    // 웹훅 수신 자체는 영향받지 않게 하기 위함(videofeed/market/subscription과
    // 동일한 "수신과 처리 분리" 원칙).
    public void handleWebhook(String payload, String signatureHeader) {
        boolean valid = billingProcessor.verifyWebhookSignature(payload, signatureHeader);
        if (!valid) {
            throw new ValidationException(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }
        eventPublisher.publishEvent(new PaymentWebhookReceivedEvent(sha256Hex(payload), payload));
    }

    /**
     * {@code PaymentWebhookConsumer}가 호출한다. 멱등 체크 기준이
     * payloadHash인 이유는 {@link PaymentWebhookReceivedEvent} 주석 참고 -
     * 실제 이벤트 타입별 처리는 아직 없고(카드 자동결제 승인이 동기 API
     * 응답으로 오기 때문에 웹훅에 의존하는 상태 전이가 아직 없음), 실제
     * 페이로드를 받아보며 확장한다.
     */
    public void processWebhookEvent(String payloadHash, String payload) {
        if (!paymentWebhookDedupStore.markProcessedIfAbsent(payloadHash)) {
            log.info("이미 처리된 토스페이먼츠 웹훅, 스킵(멱등): payloadHash={}", payloadHash);
            return;
        }
        log.info("토스페이먼츠 웹훅 처리: payloadHash={}, payload={}", payloadHash, payload);
    }

    private String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
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
     * chargeRenewal의 사전 확인({@code PaymentReader.isAlreadyProcessed})이
     * 이 값을 키로 쓴다.
     */
    private String renewalOrderId(Long subscriptionId, LocalDate currentPeriodEnd) {
        return "SUB-RENEWAL-" + subscriptionId + "-" + currentPeriodEnd;
    }
}
