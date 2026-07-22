package com.quantlime.subscription.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.domain.SubscriptionStatus;
import com.quantlime.subscription.exception.SubscriptionErrorCode;
import com.quantlime.subscription.repository.SubscriptionRepository;
import com.quantlime.user.domain.User;
import com.quantlime.user.service.UserService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public Optional<Subscription> findByUserId(Long userId) {
        return subscriptionRepository.findByUser_Id(userId);
    }

    @Transactional
    public void cancelAutoRenew(Long userId) {
        Subscription subscription = subscriptionRepository.findByUser_Id(userId)
            .orElseThrow(() -> new NotFoundException(SubscriptionErrorCode.NOT_FOUND_SUBSCRIPTION));
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new ValidationException(SubscriptionErrorCode.INVALID_SUBSCRIPTION_STATUS);
        }
        subscription.cancelAutoRenew();
        log.info("구독 자동갱신 해지: userId={}, subscriptionId={}", userId, subscription.getId());
    }

    // 카드 등록+첫 결제가 성공한 뒤 PaymentService가 호출한다. 결제
    // 승인은 트랜잭션 밖(외부 API 호출)에서 이미 끝난 상태이므로 여기선
    // 그 결과를 로컬 DB에 반영하는 것만 책임진다 - PaymentService 안에서
    // 이 메서드를 직접 만들지 않고 별도 빈으로 분리한 이유는 self-invocation
    // 시 @Transactional이 Spring AOP 프록시를 안 거쳐 무시되기 때문
    // (ScorePersistenceService를 별도 클래스로 분리했던 것과 동일한 이유,
    // CLAUDE.md Phase 3 작업기록 참고).
    @Transactional
    public Subscription activateOrResubscribe(
        Long userId, SubscriptionPlan plan, String billingKey, int installmentMonths) {
        User user = userService.getById(userId);
        return subscriptionRepository.findByUser_Id(userId)
            .map(existing -> {
                existing.resubscribe(plan, billingKey, installmentMonths, LocalDate.now());
                return existing;
            })
            .orElseGet(() -> subscriptionRepository.save(
                Subscription.activate(user, plan, billingKey, installmentMonths, LocalDate.now())));
    }

    // 자동 갱신 스케줄러가 오늘 청구해야 할 구독의 id만 뽑아온다(엔티티를
    // 그대로 넘기면 이 읽기 전용 트랜잭션이 끝난 뒤 지연 로딩 접근 시
    // LazyInitializationException이 날 수 있어 id만 넘기고, 실제 청구는
    // PaymentService가 건별로 새 트랜잭션에서 다시 조회해 처리한다).
    @Transactional(readOnly = true)
    public List<Long> findSubscriptionIdsDueForRenewal() {
        return subscriptionRepository
            .findAllByNextBillingAtAndAutoRenewTrueAndStatus(LocalDate.now(), SubscriptionStatus.ACTIVE)
            .stream()
            .map(Subscription::getId)
            .toList();
    }

    // 해지(autoRenew=false)했지만 아직 기간이 안 끝나 ACTIVE로 남아있던
    // 구독 중, 오늘 기준으로 기간이 끝난 것들을 EXPIRED로 정리한다.
    @Transactional
    public void expireLapsedSubscriptions() {
        List<Subscription> lapsed = subscriptionRepository
            .findAllExpiredWithoutAutoRenew(SubscriptionStatus.ACTIVE, LocalDate.now());
        lapsed.forEach(Subscription::expire);
        if (!lapsed.isEmpty()) {
            log.info("자동갱신 해지 후 기간 만료 처리: count={}", lapsed.size());
        }
    }
}
