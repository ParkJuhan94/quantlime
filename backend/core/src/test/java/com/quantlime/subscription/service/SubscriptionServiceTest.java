package com.quantlime.subscription.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.subscription.SubscriptionFixture;
import com.quantlime.subscription.SubscriptionPlanFixture;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.domain.SubscriptionStatus;
import com.quantlime.subscription.repository.SubscriptionRepository;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import com.quantlime.user.service.UserService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private final User user = UserFixture.createUser();
    private final SubscriptionPlan plan = SubscriptionPlanFixture.createPlan();

    @Test
    @DisplayName("[구독중인 사용자가 자동갱신을 해지하면 autoRenew가 꺼진다]")
    void cancelAutoRenew_active_turnsOffAutoRenew() {
        // given
        Long userId = 1L;
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        given(subscriptionRepository.findByUser_Id(userId)).willReturn(Optional.of(subscription));

        // when
        subscriptionService.cancelAutoRenew(userId);

        // then
        assertThat(subscription.isAutoRenew()).isFalse();
        assertThat(subscription.getNextBillingAt()).isNull();
    }

    @Test
    @DisplayName("[구독이 없는 사용자가 해지를 요청하면 404를 던진다]")
    void cancelAutoRenew_notFound_throwsNotFoundException() {
        // given
        Long userId = 1L;
        given(subscriptionRepository.findByUser_Id(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> subscriptionService.cancelAutoRenew(userId))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[이미 PAST_DUE 상태인 구독을 해지하려 하면 400을 던진다]")
    void cancelAutoRenew_notActive_throwsValidationException() {
        // given
        Long userId = 1L;
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        subscription.recordRenewalFailure();
        subscription.recordRenewalFailure();
        subscription.recordRenewalFailure();
        subscription.markPastDue();
        given(subscriptionRepository.findByUser_Id(userId)).willReturn(Optional.of(subscription));

        // when & then
        assertThatThrownBy(() -> subscriptionService.cancelAutoRenew(userId))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("[해지 후 기간이 끝난 구독을 만료 처리한다]")
    void expireLapsedSubscriptions_expiresEligibleSubscriptions() {
        // given
        Subscription subscription = SubscriptionFixture.createSubscription(user, plan);
        subscription.cancelAutoRenew();
        given(subscriptionRepository.findAllExpiredWithoutAutoRenew(
            SubscriptionStatus.ACTIVE, LocalDate.now())).willReturn(List.of(subscription));

        // when
        subscriptionService.expireLapsedSubscriptions();

        // then
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(subscription.isAutoRenew()).isFalse();
    }

    @Test
    @DisplayName("[구독 이력이 없는 사용자는 새로 구독을 시작한다]")
    void activateOrResubscribe_noExisting_createsNewSubscription() {
        // given
        Long userId = 1L;
        given(userService.getById(userId)).willReturn(user);
        given(subscriptionRepository.findByUser_Id(userId)).willReturn(Optional.empty());
        given(subscriptionRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Subscription result = subscriptionService.activateOrResubscribe(userId, plan, "bk-1", 0);

        // then
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getBillingKey()).isEqualTo("bk-1");
        assertThat(result.getPlan()).isEqualTo(plan);
    }

    @Test
    @DisplayName("[만료된 구독이 있으면 같은 row를 재구독 상태로 되돌린다]")
    void activateOrResubscribe_existingExpired_resubscribesInPlace() {
        // given
        Long userId = 1L;
        Subscription existing = SubscriptionFixture.createSubscription(user, plan);
        existing.cancelAutoRenew();
        existing.expire();
        given(userService.getById(userId)).willReturn(user);
        given(subscriptionRepository.findByUser_Id(userId)).willReturn(Optional.of(existing));

        SubscriptionPlan newPlan = SubscriptionPlanFixture.createPlan("PLAN_12M", "12개월 플랜", 12, 94800);

        // when
        Subscription result = subscriptionService.activateOrResubscribe(userId, newPlan, "bk-2", 6);

        // then
        assertThat(result).isSameAs(existing);
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getPlan()).isEqualTo(newPlan);
        assertThat(result.getBillingKey()).isEqualTo("bk-2");
        assertThat(result.getInstallmentMonths()).isEqualTo(6);
        assertThat(result.isAutoRenew()).isTrue();
    }
}
