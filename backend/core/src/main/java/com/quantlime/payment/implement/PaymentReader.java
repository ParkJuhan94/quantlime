package com.quantlime.payment.implement;

import com.quantlime.payment.domain.Payment;
import com.quantlime.payment.domain.PaymentStatus;
import com.quantlime.payment.repository.PaymentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link PaymentRepository} 조회를 감싸는 구현 레이어(Implementation) -
 * {@code PaymentService}가 Data Access(Repository)를 직접 건드리지 않고
 * 이 컴포넌트를 통해서만 결제 이력을 읽게 한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentReader {

    private final PaymentRepository paymentRepository;

    /**
     * 같은 과금주기의 갱신 결제가 이미 성공 처리됐는지 확인한다 -
     * {@code PaymentService.chargeRenewal}의 멱등 체크가 이 값으로 분기한다.
     */
    public boolean isAlreadyProcessed(String orderId) {
        return paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.DONE);
    }

    public List<Payment> getHistory(Long userId) {
        return paymentRepository.findAllByUser_IdOrderByCreatedAtDesc(userId);
    }
}
