package com.quantlime.payment.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.user.domain.User;
import jakarta.persistence.Column;
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
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static lombok.AccessLevel.PROTECTED;

// 청구 시도 이력. 최초 가입 결제가 실패한 경우(카드 등록은 됐지만 첫
// 결제가 거절된 경우)는 아직 Subscription이 없어 남길 곳이 없으므로
// 기록하지 않는다(로그만) - 이 테이블에 남는 FAILED 행은 전부 "이미
// 존재하는 구독"의 자동 갱신 실패다.
@Entity
@Table(name = "payment", uniqueConstraints = @UniqueConstraint(
    name = "uk_payment_order_id", columnNames = "order_id"))
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Payment extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
        foreignKey = @ForeignKey(NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false,
        foreignKey = @ForeignKey(NO_CONSTRAINT))
    private Subscription subscription;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "installment_months", nullable = false)
    private int installmentMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "toss_payment_key", length = 200)
    private String tossPaymentKey;

    @Column(name = "is_renewal", nullable = false)
    private boolean renewal;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Builder
    private Payment(User user, Subscription subscription, String orderId, int amount,
                     int installmentMonths, PaymentStatus status, String tossPaymentKey,
                     boolean renewal, String failReason, LocalDateTime approvedAt) {
        validatePayment(user, subscription, orderId, amount, status);
        this.user = user;
        this.subscription = subscription;
        this.orderId = orderId;
        this.amount = amount;
        this.installmentMonths = installmentMonths;
        this.status = status;
        this.tossPaymentKey = tossPaymentKey;
        this.renewal = renewal;
        this.failReason = failReason;
        this.approvedAt = approvedAt;
    }

    public static Payment success(User user, Subscription subscription, String orderId, int amount,
                                   int installmentMonths, String tossPaymentKey, boolean renewal) {
        return Payment.builder()
            .user(user)
            .subscription(subscription)
            .orderId(orderId)
            .amount(amount)
            .installmentMonths(installmentMonths)
            .status(PaymentStatus.DONE)
            .tossPaymentKey(tossPaymentKey)
            .renewal(renewal)
            .approvedAt(LocalDateTime.now())
            .build();
    }

    public static Payment failure(User user, Subscription subscription, String orderId, int amount,
                                   int installmentMonths, boolean renewal, String failReason) {
        return Payment.builder()
            .user(user)
            .subscription(subscription)
            .orderId(orderId)
            .amount(amount)
            .installmentMonths(installmentMonths)
            .status(PaymentStatus.FAILED)
            .renewal(renewal)
            .failReason(failReason)
            .build();
    }

    private void validatePayment(User user, Subscription subscription, String orderId,
                                  int amount, PaymentStatus status) {
        Assert.notNull(user, "사용자는 필수입니다.");
        Assert.notNull(subscription, "구독은 필수입니다.");
        Assert.hasText(orderId, "주문번호는 필수입니다.");
        Assert.isTrue(amount > 0, "결제 금액은 0보다 커야 합니다.");
        Assert.notNull(status, "결제 상태는 필수입니다.");
    }
}
