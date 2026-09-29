package com.quantlime.payment.implement;

import com.quantlime.payment.domain.Payment;
import com.quantlime.payment.repository.PaymentRepository;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결제 이력 엔티티 생성 + 저장을 감싸는 구현 레이어(Implementation) -
 * {@code PaymentService}가 {@link Payment#success}/{@link Payment#failure}
 * 팩토리와 {@link PaymentRepository#save}를 직접 호출하지 않고 이 컴포넌트에
 * 위임한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentAppender {

    private final PaymentRepository paymentRepository;

    public Payment appendSuccess(
        User user, Subscription subscription, String orderId, int amount,
        int installmentMonths, String tossPaymentKey, boolean renewal) {
        return paymentRepository.save(Payment.success(
            user, subscription, orderId, amount, installmentMonths, tossPaymentKey, renewal));
    }

    public void appendFailure(
        User user, Subscription subscription, String orderId, int amount,
        int installmentMonths, boolean renewal, String failReason) {
        paymentRepository.save(Payment.failure(
            user, subscription, orderId, amount, installmentMonths, renewal, failReason));
    }
}
