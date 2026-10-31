package com.quantlime.subscription.domain;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

import com.quantlime.common.domain.TimeBaseEntity;
import com.quantlime.subscription.crypto.BillingKeyConverter;
import com.quantlime.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

// 사용자 1명당 구독 1건(row)만 유지한다 - 갱신/해지/재구독은 새 row를
// 만드는 대신 같은 row를 상태 전이시킨다. 첫 결제가 성공한 시점에만
// row가 생성된다(카드 등록만 하고 첫 결제가 실패한 경우는 아무것도
// 남기지 않음 - PaymentService 참고).
@Entity
@Table(name = "subscription", uniqueConstraints = @UniqueConstraint(
    name = "uk_subscription_user", columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Subscription extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
        foreignKey = @ForeignKey(NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id", nullable = false,
        foreignKey = @ForeignKey(NO_CONSTRAINT))
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "current_period_start", nullable = false)
    private LocalDate currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private LocalDate currentPeriodEnd;

    @Column(name = "next_billing_at")
    private LocalDate nextBillingAt;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Convert(converter = BillingKeyConverter.class)
    @Column(name = "billing_key", length = 500, nullable = false)
    private String billingKey;

    // 0=일시불, 2~12=할부개월. 가입 시 고른 값을 매 갱신 결제에도 그대로 재사용한다.
    @Column(name = "installment_months", nullable = false)
    private int installmentMonths;

    @Column(name = "renewal_failure_count", nullable = false)
    private int renewalFailureCount;

    @Builder
    private Subscription(User user, SubscriptionPlan plan, LocalDate currentPeriodStart,
                          String billingKey, int installmentMonths) {
        validateSubscription(user, plan, currentPeriodStart, billingKey);
        this.user = user;
        this.plan = plan;
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodStart.plusMonths(plan.getBillingPeriodMonths());
        this.nextBillingAt = this.currentPeriodEnd;
        this.autoRenew = true;
        this.billingKey = billingKey;
        this.installmentMonths = installmentMonths;
        this.renewalFailureCount = 0;
    }

    public static Subscription activate(User user, SubscriptionPlan plan, String billingKey,
                                         int installmentMonths, LocalDate periodStart) {
        return Subscription.builder()
            .user(user)
            .plan(plan)
            .currentPeriodStart(periodStart)
            .billingKey(billingKey)
            .installmentMonths(installmentMonths)
            .build();
    }

    // PAST_DUE/EXPIRED 상태였던 기존 row에 새 결제를 얹어 재구독시킨다
    // (사용자 1명당 row 1건 유지 원칙 - 새 row를 만들지 않음).
    public void resubscribe(SubscriptionPlan plan, String billingKey, int installmentMonths,
                             LocalDate periodStart) {
        Assert.notNull(plan, "플랜은 필수입니다.");
        Assert.hasText(billingKey, "빌링키는 필수입니다.");
        this.plan = plan;
        this.billingKey = billingKey;
        this.installmentMonths = installmentMonths;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodStart.plusMonths(plan.getBillingPeriodMonths());
        this.nextBillingAt = this.currentPeriodEnd;
        this.status = SubscriptionStatus.ACTIVE;
        this.autoRenew = true;
        this.renewalFailureCount = 0;
    }

    // 자동 갱신 재시도 스케줄(+N일 뒤 재시도)을 위해 다음 결제 예정일만
    // 앞으로 미룬다 - 상태는 그대로 ACTIVE 유지(재시도 소진 전까지는
    // 여전히 구독중으로 취급).
    public void scheduleRenewalRetry(LocalDate nextAttemptAt) {
        this.nextBillingAt = nextAttemptAt;
    }

    // 자동 갱신 결제가 성공했을 때 - 현재 종료일이 다음 주기의 시작일이 된다.
    public void renew() {
        LocalDate newStart = this.currentPeriodEnd;
        LocalDate newEnd = newStart.plusMonths(this.plan.getBillingPeriodMonths());
        this.currentPeriodStart = newStart;
        this.currentPeriodEnd = newEnd;
        this.nextBillingAt = newEnd;
        this.status = SubscriptionStatus.ACTIVE;
        this.renewalFailureCount = 0;
    }

    /**
     * 이 갱신 요청을 지금 처리해도 되는지(2026-10-31, 아웃박스 도입). 아웃박스는 브로커 장애가 길면 요청을 몇 시간~
     * 며칠 늦게 보낼 수 있고 발행은 at-least-once다. 결제 orderId 멱등은 현재 기간 종료일 기준이라, 갱신이 성공해
     * 기간이 넘어간 뒤 같은 요청이 다시 오면 다음 주기를 미리 결제하게 된다 - 요청이 가리키는 결제 예정일이 그대로인지,
     * 그 사이 해지·만료되지 않았는지를 본다. {@code requestedBillingDate}가 없는 예전 메시지는 상태만 확인한다.
     */
    public boolean isRenewalDue(@Nullable LocalDate requestedBillingDate) {
        if (status != SubscriptionStatus.ACTIVE || !autoRenew || nextBillingAt == null) {
            return false;
        }
        return requestedBillingDate == null || nextBillingAt.equals(requestedBillingDate);
    }

    public void recordRenewalFailure() {
        this.renewalFailureCount++;
    }

    public void markPastDue() {
        this.status = SubscriptionStatus.PAST_DUE;
    }

    // 자동갱신 재시도를 모두 소진했거나(PAST_DUE), 해지 후 현재 주기가
    // 끝난 경우 - 더 이상 프리미엄 상태가 아님을 명시적으로 표시한다.
    public void expire() {
        this.status = SubscriptionStatus.EXPIRED;
        this.autoRenew = false;
        this.nextBillingAt = null;
    }

    // 즉시 차단하지 않고 현재 주기가 끝날 때까지는 그대로 이용 가능,
    // 이후 자동 재결제만 안 함(SubscriptionRenewalScheduler가 기간 종료
    // 후 expire() 호출).
    public void cancelAutoRenew() {
        this.autoRenew = false;
        this.nextBillingAt = null;
    }

    private void validateSubscription(User user, SubscriptionPlan plan,
                                       LocalDate currentPeriodStart, String billingKey) {
        Assert.notNull(user, "사용자는 필수입니다.");
        Assert.notNull(plan, "플랜은 필수입니다.");
        Assert.notNull(currentPeriodStart, "구독 시작일은 필수입니다.");
        Assert.hasText(billingKey, "빌링키는 필수입니다.");
    }
}
