package com.quantlime.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quantlime.subscription.SubscriptionFixture;
import com.quantlime.subscription.SubscriptionPlanFixture;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PaymentTest {

    private User user;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        user = UserFixture.createUser();
        subscription = SubscriptionFixture.createSubscription(user, SubscriptionPlanFixture.createPlan());
    }

    @Test
    @DisplayName("[성공 결제는 DONE 상태에 결제키와 승인시각이 채워지고 실패 사유는 없다]")
    void success_setsDoneStatusKeyAndApprovedAt() {
        Payment payment = Payment.success(user, subscription, "order-1", 9900, 0, "pay-key-1", true);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getTossPaymentKey()).isEqualTo("pay-key-1");
        assertThat(payment.getApprovedAt()).isNotNull();
        assertThat(payment.getFailReason()).isNull();
        assertThat(payment.isRenewal()).isTrue();
        assertThat(payment.getOrderId()).isEqualTo("order-1");
        assertThat(payment.getAmount()).isEqualTo(9900);
        assertThat(payment.getUser()).isSameAs(user);
        assertThat(payment.getSubscription()).isSameAs(subscription);
    }

    @Test
    @DisplayName("[실패 결제는 FAILED 상태에 사유가 남고 결제키와 승인시각은 없다]")
    void failure_setsFailedStatusAndReason() {
        Payment payment = Payment.failure(user, subscription, "order-2", 9900, 3, true, "카드 한도 초과");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailReason()).isEqualTo("카드 한도 초과");
        assertThat(payment.getTossPaymentKey()).isNull();
        assertThat(payment.getApprovedAt()).isNull();
        assertThat(payment.getInstallmentMonths()).isEqualTo(3);
    }

    @Test
    @DisplayName("[최초 가입 결제는 renewal=false로 기록된다]")
    void success_initialPayment_isNotRenewal() {
        Payment payment = Payment.success(user, subscription, "order-3", 9900, 0, "pay-key-3", false);

        assertThat(payment.isRenewal()).isFalse();
    }

    @Test
    @DisplayName("[사용자·구독이 없거나 주문번호가 비었거나 금액이 0 이하면 생성할 수 없다]")
    void invalidArguments_areRejected() {
        assertThatThrownBy(() -> Payment.success(null, subscription, "o", 1000, 0, "k", false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.success(user, null, "o", 1000, 0, "k", false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.success(user, subscription, " ", 1000, 0, "k", false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.success(user, subscription, "o", 0, 0, "k", false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.failure(user, subscription, "o", -1, 0, true, "r"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
