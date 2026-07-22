package com.quantlime.payment.service;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.tosspayments.TossPaymentsApiClient;
import com.quantlime.infra.tosspayments.TossPaymentsProperties;
import com.quantlime.infra.tosspayments.TossWebhookVerifier;
import com.quantlime.infra.tosspayments.dto.TossBillingKeyResponse;
import com.quantlime.infra.tosspayments.dto.TossPaymentApprovalResponse;
import com.quantlime.payment.domain.Payment;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String CUSTOMER_KEY_PREFIX = "quantlime-user-";
    private static final String ORDER_NAME = "QuantLime 프리미엄 구독";
    private static final int MAX_RENEWAL_RETRY = 3;

    private final SubscriptionPlanService subscriptionPlanService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final TossPaymentsApiClient tossPaymentsApiClient;
    private final TossWebhookVerifier tossWebhookVerifier;
    private final TossPaymentsProperties tossPaymentsProperties;

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
        return subscription;
    }

    // 자동 갱신 스케줄러가 건별로 호출한다. 실패해도 예외를 던지지 않고
    // 내부에서 흡수한다(SubscriptionRenewalScheduler가 개별 실패로 배치
    // 전체를 멈추지 않도록).
    @Transactional
    public void chargeRenewal(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException(SubscriptionErrorCode.NOT_FOUND_SUBSCRIPTION));
        User user = subscription.getUser();
        SubscriptionPlan plan = subscription.getPlan();
        String customerKey = toCustomerKey(user.getId());
        String orderId = generateOrderId();

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
            paymentRepository.save(Payment.failure(
                user, subscription, orderId, plan.getPriceWon(),
                subscription.getInstallmentMonths(), true, e.getMessage()));
            subscription.recordRenewalFailure();
            if (subscription.getRenewalFailureCount() >= MAX_RENEWAL_RETRY) {
                subscription.markPastDue();
                log.warn("구독 자동 갱신 최종 실패(재시도 소진), PAST_DUE 전환: "
                        + "userId={}, subscriptionId={}, orderId={}, error={}",
                    user.getId(), subscriptionId, orderId, e.getMessage());
            } else {
                subscription.scheduleRenewalRetry(LocalDate.now().plusDays(1));
                log.warn("구독 자동 갱신 결제 실패, 내일 재시도: "
                        + "userId={}, subscriptionId={}, orderId={}, 시도횟수={}, error={}",
                    user.getId(), subscriptionId, orderId,
                    subscription.getRenewalFailureCount(), e.getMessage());
            }
        }
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
}
