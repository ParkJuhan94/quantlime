package com.quantlime.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.implement.SubscriptionPlanAppender;
import com.quantlime.subscription.implement.SubscriptionPlanReader;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SubscriptionPlanInitializerTest {

    @Mock
    private SubscriptionPlanReader subscriptionPlanReader;

    @Mock
    private SubscriptionPlanAppender subscriptionPlanAppender;

    @InjectMocks
    private SubscriptionPlanInitializer initializer;

    @Test
    @DisplayName("[플랜이 하나도 없으면 3·6·12개월 플랜을 월 7,900원 × 개월수로 시딩한다]")
    void run_noPlans_seedsThreePlansAtMonthlyPriceTimesMonths() {
        given(subscriptionPlanReader.existsByCode(any())).willReturn(false);

        initializer.run(null);

        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanAppender, times(3)).save(captor.capture());
        List<SubscriptionPlan> saved = captor.getAllValues();
        assertThat(saved).extracting(SubscriptionPlan::getCode).containsExactly("PLAN_3M", "PLAN_6M", "PLAN_12M");
        assertThat(saved).extracting(SubscriptionPlan::getBillingPeriodMonths).containsExactly(3, 6, 12);
        assertThat(saved).extracting(SubscriptionPlan::getPriceWon).containsExactly(23_700, 47_400, 94_800);
    }

    @Test
    @DisplayName("[이미 있는 코드는 건너뛰고 없는 코드만 저장한다(재기동 멱등)]")
    void run_someExist_savesOnlyMissing() {
        given(subscriptionPlanReader.existsByCode("PLAN_3M")).willReturn(true);
        given(subscriptionPlanReader.existsByCode("PLAN_6M")).willReturn(true);
        given(subscriptionPlanReader.existsByCode("PLAN_12M")).willReturn(false);

        initializer.run(null);

        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanAppender, times(1)).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("PLAN_12M");
    }

    @Test
    @DisplayName("[모든 플랜이 이미 있으면 아무것도 저장하지 않는다]")
    void run_allExist_savesNothing() {
        given(subscriptionPlanReader.existsByCode(any())).willReturn(true);

        initializer.run(null);

        verify(subscriptionPlanAppender, never()).save(any());
    }
}
