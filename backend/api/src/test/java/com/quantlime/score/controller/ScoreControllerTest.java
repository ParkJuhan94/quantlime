package com.quantlime.score.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.score.dto.response.ScoreRankingResponse;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 스코어/백테스트는 구독자 전용이다(CLAUDE.md §6) - 경로 자체는 permitAll이고 컨트롤러가
 * Subscription.status=ACTIVE로 403(SUB_006)을 판정하므로, 게이팅 규칙이 이 테스트의 핵심이다.
 * 스코어 계산/조회 자체는 ScoreServiceTest 몫이라 ScoreService는 목으로 격리한다.
 */
@Tag("integration")
class ScoreControllerTest extends MockedServicesApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User subscriber;
    private User freeUser;
    private String subscriberAuth;
    private String freeAuth;

    @BeforeEach
    void setUp() {
        subscriber = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "sub"));
        freeUser = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "free"));
        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlanFixture.createPlan());
        subscriptionRepository.save(SubscriptionFixture.createSubscription(subscriber, plan));
        subscriberAuth = "Bearer " + jwtTokenProvider.createAccessToken(subscriber.getId(), subscriber.getRole());
        freeAuth = "Bearer " + jwtTokenProvider.createAccessToken(freeUser.getId(), freeUser.getRole());
    }

    private ScoreRankingResponse ranking(String code) {
        return new ScoreRankingResponse(code, "종목" + code, "섹터", null, 1.0, 2.0, 70.0, 10.0, 20.0, 90.0,
            "BUY", false, null, false, null);
    }

    @Test
    @DisplayName("[비로그인/비구독자는 403(SUB_006), 스코어 서비스는 호출되지 않는다]")
    void nonSubscriber_isForbidden() throws Exception {
        mockMvc.perform(get("/api/dashboard/scores")).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SUB_006"));
        mockMvc.perform(get("/api/dashboard/scores").header("Authorization", freeAuth))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/stocks/{code}/score", "005930").header("Authorization", freeAuth))
            .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(scoreService);
    }

    @Test
    @DisplayName("[구독자가 watchlistOnly=false로 호출하면 전체 랭킹(limit/scope 전달)을 받는다]")
    void subscriber_allRanking() throws Exception {
        given(scoreService.getAllStocksScoreRanking(20, "domestic")).willReturn(List.of(ranking("000001")));

        mockMvc.perform(get("/api/dashboard/scores").header("Authorization", subscriberAuth)
                .param("watchlistOnly", "false").param("limit", "20").param("scope", "domestic"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].stockCode").value("000001"));
    }

    @Test
    @DisplayName("[watchlistOnly 기본값(true)이면 내 관심종목 스코어를 조회한다]")
    void subscriber_watchlistDefault() throws Exception {
        given(scoreService.getDashboardScores(subscriber.getId(), "all")).willReturn(List.of(ranking("000002")));

        mockMvc.perform(get("/api/dashboard/scores").header("Authorization", subscriberAuth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].stockCode").value("000002"));
        verify(scoreService).getDashboardScores(subscriber.getId(), "all");
    }

    @Test
    @DisplayName("[limit는 1~50, scope는 all/domestic/overseas만 허용한다(400)]")
    void invalidParams_return400() throws Exception {
        mockMvc.perform(get("/api/dashboard/scores").header("Authorization", subscriberAuth).param("limit", "0"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dashboard/scores").header("Authorization", subscriberAuth).param("limit", "51"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dashboard/scores").header("Authorization", subscriberAuth).param("scope", "asia"))
            .andExpect(status().isBadRequest());
    }
}
