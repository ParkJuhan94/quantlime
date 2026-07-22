package com.quantlime.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.auth.cookie.RefreshTokenCookieProvider;
import com.quantlime.auth.dto.request.SocialLoginRequest;
import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.infra.oauth.OAuthClientDispatcher;
import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.UserRole;
import jakarta.servlet.http.Cookie;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
    @DisplayName("[소셜 로그인 성공 시 액세스 토큰은 바디로, 리프레시 토큰은 httpOnly 쿠키로 내려온다]")
    void login_success_returns200AndTokens() throws Exception {
        // given
        OAuthUserInfo userInfo = new OAuthUserInfo(
            OAuthProvider.GOOGLE, "google-id-1", "test@gmail.com", "테스트유저", null);
        given(oAuthClientDispatcher.fetch(eq(OAuthProvider.GOOGLE), any(), any()))
            .willReturn(userInfo);

        SocialLoginRequest request = new SocialLoginRequest("auth-code", "http://localhost/cb");

        // when & then: 리프레시 토큰은 더 이상 응답 바디에 없다(쿠키로만 전달)
        mockMvc.perform(post("/api/auth/login/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(cookie().exists(RefreshTokenCookieProvider.COOKIE_NAME))
            .andExpect(cookie().httpOnly(RefreshTokenCookieProvider.COOKIE_NAME, true))
            .andExpect(cookie().path(RefreshTokenCookieProvider.COOKIE_NAME, "/api/auth"));
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
    @DisplayName("[유효한 액세스 토큰으로 로그아웃하면 204와 함께 쿠키를 지운다]")
    void logout_withValidToken_returns204() throws Exception {
        // given
        String accessToken = jwtTokenProvider.createAccessToken(1L, UserRole.USER);

        // when & then: maxAge=0으로 재발급된 쿠키가 브라우저의 기존 쿠키를 지운다
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent())
            .andExpect(cookie().maxAge(RefreshTokenCookieProvider.COOKIE_NAME, 0));
    }

    @Test
    @DisplayName("[리프레시 토큰을 액세스 토큰처럼 Authorization 헤더에 실어도 인증되지 않는다]")
    void protectedEndpoint_withRefreshTokenAsBearer_returns401() throws Exception {
        // given: JwtAuthenticationFilter가 type=refresh를 걸러내는지 검증
        String refreshToken = jwtTokenProvider.createRefreshToken(1L);

        // when & then
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + refreshToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[쿠키 없이 재발급을 요청하면 401을 반환한다]")
    void reissue_withoutCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/reissue"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[유효하지 않은 리프레시 토큰 쿠키로 재발급하면 401을 반환한다]")
    void reissue_invalidTokenCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/reissue")
                .cookie(new Cookie(RefreshTokenCookieProvider.COOKIE_NAME, "invalid-refresh-token")))
            .andExpect(status().isUnauthorized());
    }
}
