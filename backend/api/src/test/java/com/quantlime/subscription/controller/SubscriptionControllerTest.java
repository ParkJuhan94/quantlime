package com.quantlime.subscription.controller;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.infra.tosspayments.TossPaymentsApiClient;
import com.quantlime.infra.tosspayments.dto.TossBillingKeyResponse;
import com.quantlime.infra.tosspayments.dto.TossPaymentApprovalResponse;
import com.quantlime.subscription.SubscriptionPlanFixture;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.repository.SubscriptionPlanRepository;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class SubscriptionControllerTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // 실제 토스페이먼츠를 호출하지 않도록 격리(WatchlistControllerTest가
    // TossApiClient를 목킹하는 것과 동일한 이유).
    @MockBean
    private TossPaymentsApiClient tossPaymentsApiClient;

    private String accessToken;
    private SubscriptionPlan plan;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(UserFixture.createUser());
        accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        plan = subscriptionPlanRepository.save(SubscriptionPlanFixture.createPlan());
    }

    @Test
    @DisplayName("[판매중인 구독 플랜 목록을 조회한다]")
    void getPlans_returnsSeededPlan() throws Exception {
        mockMvc.perform(get("/api/subscription/plans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value(plan.getCode()));
    }

    @Test
    @DisplayName("[구독 이력이 없으면 customerKey만 담긴 응답을 반환한다]")
    void getMySubscription_noSubscription_returnsNullSubscriptionWithCustomerKey() throws Exception {
        mockMvc.perform(get("/api/subscription/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customerKey").isNotEmpty())
            .andExpect(jsonPath("$.subscription").isEmpty());
    }

    @Test
    @DisplayName("[토큰 없이 내 구독을 조회하면 401을 반환한다]")
    void getMySubscription_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/subscription/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[빌링키 발급과 첫 결제가 성공하면 201과 구독 정보를 반환한다]")
    void issueBillingKey_success_returns201() throws Exception {
        given(tossPaymentsApiClient.issueBillingKey(anyString(), anyString()))
            .willReturn(new TossBillingKeyResponse("bk-1", "customer-1", "국민", "1234", "now"));
        given(tossPaymentsApiClient.chargeWithBillingKey(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
            .willReturn(new TossPaymentApprovalResponse(
                "pk-1", "order-1", "구독", "DONE", plan.getPriceWon(), "카드", "now"));

        mockMvc.perform(post("/api/subscription/billing-key")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authKey\":\"auth-key\",\"planCode\":\"" + plan.getCode()
                    + "\",\"installmentMonths\":0}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.planCode").value(plan.getCode()))
            .andExpect(jsonPath("$.status").value("구독중"));

        mockMvc.perform(get("/api/subscription/payments")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].orderId").value("order-1"));

        mockMvc.perform(post("/api/subscription/cancel")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("[존재하지 않는 플랜 코드로 요청하면 404를 반환한다]")
    void issueBillingKey_unknownPlanCode_returns404() throws Exception {
        mockMvc.perform(post("/api/subscription/billing-key")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authKey\":\"auth-key\",\"planCode\":\"NOT_EXIST\",\"installmentMonths\":0}"))
            .andExpect(status().isNotFound());
    }
}
