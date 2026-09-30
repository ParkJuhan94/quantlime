package com.quantlime.backtest.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.backtest.dto.response.BacktestResponse;
import com.quantlime.backtest.service.BacktestService;
import com.quantlime.subscription.SubscriptionFixture;
import com.quantlime.subscription.SubscriptionPlanFixture;
import com.quantlime.subscription.domain.SubscriptionPlan;
import com.quantlime.subscription.repository.SubscriptionPlanRepository;
import com.quantlime.subscription.repository.SubscriptionRepository;
import com.quantlime.support.MockedServicesApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 구독자 전용 게이팅(SUB_006)이 핵심이다 - 백테스트 계산 자체는 BacktestServiceTest 몫이라 서비스는 목으로 격리한다
@Tag("integration")
class BacktestControllerTest extends MockedServicesApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String subscriberAuth;
    private String freeAuth;

    @BeforeEach
    void setUp() {
        User subscriber = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "sub"));
        User free = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "free"));
        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlanFixture.createPlan());
        subscriptionRepository.save(SubscriptionFixture.createSubscription(subscriber, plan));
        subscriberAuth = "Bearer " + jwtTokenProvider.createAccessToken(subscriber.getId(), subscriber.getRole());
        freeAuth = "Bearer " + jwtTokenProvider.createAccessToken(free.getId(), free.getRole());
    }

    @Test
    @DisplayName("[비로그인/비구독자는 403(SUB_006)이고 백테스트 서비스는 호출되지 않는다]")
    void nonSubscriber_isForbidden() throws Exception {
        mockMvc.perform(get("/api/backtest/{code}", "005930"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SUB_006"));
        mockMvc.perform(get("/api/backtest/{code}", "005930").header("Authorization", freeAuth))
            .andExpect(status().isForbidden());

        verifyNoInteractions(backtestService);
    }

    @Test
    @DisplayName("[구독자는 백테스트 결과를 조회한다]")
    void subscriber_getsResult() throws Exception {
        given(backtestService.getBacktestResult("005930"))
            .willReturn(new BacktestResponse("005930", "v3.0", LocalDate.of(2026, 9, 30), List.of(), List.of()));

        mockMvc.perform(get("/api/backtest/{code}", "005930").header("Authorization", subscriberAuth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockCode").value("005930"))
            .andExpect(jsonPath("$.scoreVersion").value("v3.0"));
    }
}
