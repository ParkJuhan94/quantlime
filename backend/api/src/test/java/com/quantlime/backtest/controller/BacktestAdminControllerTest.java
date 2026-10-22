package com.quantlime.backtest.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.batch.common.BatchLaunchRejectedException;
import com.quantlime.support.MockedServicesApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.domain.UserRole;
import com.quantlime.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 실제 백테스트는 수십 분짜리 연산이라 4개 서비스를 모두 목으로 격리하고, 관리자 인가와 파라미터 전달만 검증한다
@Tag("integration")
class BacktestAdminControllerTest extends MockedServicesApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String adminAuth;
    private String userAuth;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "admin"));
        User user = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "user"));
        adminAuth = "Bearer " + jwtTokenProvider.createAccessToken(admin.getId(), UserRole.ADMIN);
        userAuth = "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    @Test
    @DisplayName("[일반 사용자는 403이고 어떤 백테스트 Job도 시작되지 않는다]")
    void normalUser_isForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/backtest/prepare-dataset").header("Authorization", userAuth))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/backtest/run-universe").header("Authorization", userAuth))
            .andExpect(status().isForbidden());

        verifyNoInteractions(backtestBatchLauncher, backtestService);
    }

    @Test
    @DisplayName("[관리자는 데이터셋 준비를 Job으로 시작(202)하고, 종목 백테스트는 동기로 실행한다]")
    void admin_triggersPrepareAndRun() throws Exception {
        given(backtestBatchLauncher.launchDataset()).willReturn(11L);

        mockMvc.perform(post("/api/admin/backtest/prepare-dataset").header("Authorization", adminAuth))
            .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/admin/backtest/run").param("stockCode", "005930").header("Authorization", adminAuth))
            .andExpect(status().isOk());

        verify(backtestBatchLauncher).launchDataset();
        verify(backtestService).runBacktest("005930");
    }

    @Test
    @DisplayName("[유니버스 백테스트는 force 기본값 false, 지정하면 그대로 전달한다]")
    void admin_runUniverse_forceParam() throws Exception {
        given(backtestBatchLauncher.launchUniverse(false)).willReturn(12L);
        given(backtestBatchLauncher.launchUniverse(true)).willReturn(13L);

        mockMvc.perform(post("/api/admin/backtest/run-universe").header("Authorization", adminAuth))
            .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/admin/backtest/run-universe").param("force", "true")
                .header("Authorization", adminAuth))
            .andExpect(status().isAccepted());

        verify(backtestBatchLauncher).launchUniverse(false);
        verify(backtestBatchLauncher).launchUniverse(true);
    }

    @Test
    @DisplayName("[횡단면 백테스트: market 생략 시 null(전 시장), 지정 시 해당 시장을 넘기고 기본 nullRepeats=200이다]")
    void admin_crossSectional_marketRouting() throws Exception {
        given(backtestBatchLauncher.launchCrossSectional(null, "v3.0", false, 200)).willReturn(14L);
        given(backtestBatchLauncher.launchCrossSectional("KOSPI", "v3.0", true, 50)).willReturn(15L);

        mockMvc.perform(post("/api/admin/backtest/cross-sectional").param("scoreVersion", "v3.0")
                .header("Authorization", adminAuth))
            .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/admin/backtest/cross-sectional").param("scoreVersion", "v3.0")
                .param("market", "KOSPI").param("nullTest", "true").param("nullRepeats", "50")
                .header("Authorization", adminAuth))
            .andExpect(status().isAccepted());

        verify(backtestBatchLauncher).launchCrossSectional(null, "v3.0", false, 200);
        verify(backtestBatchLauncher).launchCrossSectional("KOSPI", "v3.0", true, 50);
    }

    @Test
    @DisplayName("[지원하지 않는 시장은 400이고 Job을 시작하지 않는다]")
    void admin_crossSectional_unsupportedMarket_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/admin/backtest/cross-sectional").param("scoreVersion", "v3.0")
                .param("market", "TOKYO").header("Authorization", adminAuth))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(backtestBatchLauncher);
    }

    @Test
    @DisplayName("[다른 백테스트 Job이 실행 중이라 거부되면 409다]")
    void admin_rejectedLaunch_isConflict() throws Exception {
        given(backtestBatchLauncher.launchUniverse(false))
            .willThrow(new BatchLaunchRejectedException("이미 실행 중인 배치가 있습니다"));

        mockMvc.perform(post("/api/admin/backtest/run-universe").header("Authorization", adminAuth))
            .andExpect(status().isConflict());
    }
}
