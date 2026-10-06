package com.quantlime.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.subscription.SubscriptionPlanFixture;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.implement.SubscriptionPlanReader;
import com.quantlime.subscription.repository.SubscriptionPlanRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SubscriptionPlanServiceTest {

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    private SubscriptionPlanService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPlanService(new SubscriptionPlanReader(subscriptionPlanRepository));
    }

    @Test
    @DisplayName("[활성 플랜은 청구 주기 오름차순으로 조회한다]")
    void getActivePlans_delegatesToOrderedQuery() {
        // given
        List<SubscriptionPlan> plans = List.of(SubscriptionPlanFixture.createPlan());
        given(subscriptionPlanRepository.findAllByActiveTrueOrderByBillingPeriodMonthsAsc()).willReturn(plans);

        // when & then
        assertThat(service.getActivePlans()).isSameAs(plans);
    }

    @Test
    @DisplayName("[코드로 플랜을 조회하고, 없으면 NotFoundException]")
    void getByCode_foundOrThrows() {
        // given
        SubscriptionPlan plan = SubscriptionPlanFixture.createPlan();
        given(subscriptionPlanRepository.findByCode("MONTHLY")).willReturn(Optional.of(plan));
        given(subscriptionPlanRepository.findByCode("NOPE")).willReturn(Optional.empty());

        // when & then
        assertThat(service.getByCode("MONTHLY")).isSameAs(plan);
        assertThatThrownBy(() -> service.getByCode("NOPE")).isInstanceOf(NotFoundException.class);
    }
}
