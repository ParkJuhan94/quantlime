package com.quantlime.subscription.repository;

import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // plan은 LAZY라 fetch join 없이 조회하면, open-in-view:false 환경에서
    // 트랜잭션 밖(컨트롤러의 매퍼 호출 등)에서 subscription.getPlan() 접근 시
    // LazyInitializationException이 난다(GET /api/subscription/me에서 실제 발견).
    @Query("select s from Subscription s join fetch s.plan where s.user.id = :userId")
    Optional<Subscription> findByUser_Id(@Param("userId") Long userId);

    // 자동 갱신 스케줄러 - 오늘이 다음 결제일이고 자동갱신이 켜진 구독만 조회
    List<Subscription> findAllByNextBillingAtAndAutoRenewTrueAndStatus(
        LocalDate nextBillingAt, SubscriptionStatus status);

    // 해지(autoRenew=false)했지만 아직 만료 처리가 안 된, 현재 주기가
    // 이미 끝난 구독을 정리하기 위한 조회(SubscriptionRenewalScheduler).
    @Query("select s from Subscription s where s.autoRenew = false "
        + "and s.status = :status and s.currentPeriodEnd < :today")
    List<Subscription> findAllExpiredWithoutAutoRenew(
        @Param("status") SubscriptionStatus status, @Param("today") LocalDate today);
}
