package com.quantlime.subscription.repository;

import com.quantlime.subscription.domain.SubscriptionPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByCode(String code);

    boolean existsByCode(String code);

    List<SubscriptionPlan> findAllByActiveTrueOrderByBillingPeriodMonthsAsc();
}
