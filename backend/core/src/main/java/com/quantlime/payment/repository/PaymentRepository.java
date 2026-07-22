package com.quantlime.payment.repository;

import com.quantlime.payment.domain.Payment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
}
