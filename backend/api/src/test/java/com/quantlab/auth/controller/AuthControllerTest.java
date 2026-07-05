package com.quantlab.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.auth.dto.request.ReissueRequest;
import com.quantlab.auth.dto.request.SocialLoginRequest;
import com.quantlab.auth.jwt.JwtTokenProvider;
import com.quantlab.infra.oauth.OAuthClientDispatcher;
import com.quantlab.infra.oauth.dto.OAuthUserInfo;
import com.quantlab.support.ApiTestSupport;
import com.quantlab.user.domain.OAuthProvider;
import com.quantlab.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class AuthControllerTest extends ApiTestSupport {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private OAuthClientDispatcher oAuthClientDispatcher;

    @Test
    @DisplayName("[소셜 로그인 성공 시 토큰을 발급하고 200을 반환한다]")
    void login_success_returns200AndTokens() throws Exception {
        // given
        OAuthUserInfo userInfo = new OAuthUserInfo(
            OAuthProvider.GOOGLE, "google-id-1", "test@gmail.com", "테스트유저", null);
        given(oAuthClientDispatcher.fetch(eq(OAuthProvider.GOOGLE), any(), any()))
            .willReturn(userInfo);

        SocialLoginRequest request = new SocialLoginRequest("auth-code", "http://localhost/cb");

        // when & then
        mockMvc.perform(post("/api/auth/login/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("[지원하지 않는 provider로 로그인하면 400을 반환한다]")
    void login_unsupportedProvider_returns400() throws Exception {
        // given
        SocialLoginRequest request = new SocialLoginRequest("auth-code", "http://localhost/cb");

        // when & then
        mockMvc.perform(post("/api/auth/login/facebook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[토큰 없이 보호된 엔드포인트에 접근하면 401을 반환한다]")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[유효한 액세스 토큰으로 로그아웃하면 204를 반환한다]")
    void logout_withValidToken_returns204() throws Exception {
        // given
        String accessToken = jwtTokenProvider.createAccessToken(1L, UserRole.USER);

        // when & then
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("[유효하지 않은 리프레시 토큰으로 재발급하면 401을 반환한다]")
    void reissue_invalidToken_returns401() throws Exception {
        // given
        ReissueRequest request = new ReissueRequest("invalid-refresh-token");

        // when & then
        mockMvc.perform(post("/api/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }
}
