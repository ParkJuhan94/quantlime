package com.quantlime.subscription.implement;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionStatus;
import com.quantlime.subscription.exception.SubscriptionErrorCode;
import com.quantlime.subscription.repository.SubscriptionRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link SubscriptionRepository} 조회를 감싸는 구현 레이어(Implementation) -
 * {@code PaymentService}(다른 도메인)가 구독 조회를 위해 Repository를 직접
 * 건드리지 않고 이 컴포넌트를 통해서만 읽게 한다. Implementation 레이어는
 * 서로 참조 가능하다는 규칙에 따라 payment 쪽에서 가져다 쓴다.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionReader {

    private final SubscriptionRepository subscriptionRepository;

    public Subscription getById(Long subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException(SubscriptionErrorCode.NOT_FOUND_SUBSCRIPTION));
    }

    public Optional<Subscription> findByUserId(Long userId) {
        return subscriptionRepository.findByUser_Id(userId);
    }

    public List<Long> findAllUserIdsByStatus(SubscriptionStatus status) {
        return subscriptionRepository.findAllUserIdsByStatus(status);
    }
}
