package com.quantlime.subscription.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.dto.response.SubscriptionPlanResponse;
import com.quantlime.subscription.dto.response.SubscriptionResponse;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class SubscriptionMapperTest {

    private final SubscriptionPlan plan = SubscriptionPlan.of("PREMIUM_3M", "프리미엄 3개월", 3, 27000);

    @Test
    @DisplayName("[플랜 응답은 코드·이름·청구 개월·가격을 그대로 옮긴다]")
    void toSubscriptionPlanResponse_copiesFields() {
        SubscriptionPlanResponse response = SubscriptionMapper.toSubscriptionPlanResponse(plan);

        assertThat(response).isEqualTo(new SubscriptionPlanResponse("PREMIUM_3M", "프리미엄 3개월", 3, 27000));
    }

    @Test
    @DisplayName("[구독 응답은 상태를 한글 라벨로, 기간은 플랜 개월 수만큼 더한 값으로 담는다]")
    void toSubscriptionResponse_usesStatusLabelAndPeriod() {
        Subscription subscription = Subscription.activate(
            UserFixture.createUser(OAuthProvider.GOOGLE, "sub-user"), plan, "billing-key", 6,
            LocalDate.of(2026, 9, 1));

        SubscriptionResponse response = SubscriptionMapper.toSubscriptionResponse(subscription);

        assertThat(response.planCode()).isEqualTo("PREMIUM_3M");
        assertThat(response.planName()).isEqualTo("프리미엄 3개월");
        assertThat(response.status()).isEqualTo("구독중");
        assertThat(response.currentPeriodStart()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.currentPeriodEnd()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(response.nextBillingAt()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(response.autoRenew()).isTrue();
        assertThat(response.installmentMonths()).isEqualTo(6);
    }
}
