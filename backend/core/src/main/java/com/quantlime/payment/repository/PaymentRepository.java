package com.quantlime.payment.repository;

import com.quantlime.payment.domain.Payment;
import com.quantlime.payment.domain.PaymentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    // 구독 갱신 멱등성 체크(2026-09-24, 카프카 다도메인 확장) - 결정론적
    // orderId(subscriptionId+과금주기)로 이미 성공 처리된 결제가 있으면
    // Toss를 다시 부르지 않는다. PaymentService.chargeRenewal 참고.
    boolean existsByOrderIdAndStatus(String orderId, PaymentStatus status);
}
