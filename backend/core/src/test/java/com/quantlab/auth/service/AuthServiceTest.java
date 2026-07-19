package com.quantlab.auth.service;

import com.quantlab.auth.dto.request.SocialLoginRequest;
import com.quantlab.auth.jwt.JwtTokenProvider;
import com.quantlab.auth.token.RefreshTokenStore;
import com.quantlab.common.exception.UnauthorizedException;
import com.quantlab.infra.oauth.OAuthClientDispatcher;
import com.quantlab.infra.oauth.dto.OAuthUserInfo;
import com.quantlab.user.UserFixture;
import com.quantlab.user.domain.OAuthProvider;
import com.quantlab.user.domain.User;
import com.quantlab.user.domain.UserRole;
import com.quantlab.user.service.UserService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private OAuthClientDispatcher oAuthClientDispatcher;

    @Mock
    private UserService userService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("[소셜 로그인 성공 시 사용자를 조회/생성하고 토큰을 발급한다]")
    void login_success_issuesTokens() {
        // given
        User user = UserFixture.createUser();
        setUserId(user, 1L);
        OAuthUserInfo userInfo = new OAuthUserInfo(
            OAuthProvider.GOOGLE, "google-id", "test@example.com", "테스트유저", null);
        SocialLoginRequest request = new SocialLoginRequest("auth-code", "http://localhost/cb");

        given(oAuthClientDispatcher.fetch(OAuthProvider.GOOGLE, "auth-code", "http://localhost/cb"))
            .willReturn(userInfo);
        given(userService.findOrCreate(userInfo)).willReturn(user);
        given(jwtTokenProvider.createAccessToken(1L, UserRole.USER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(1L)).willReturn("refresh-token");
        given(jwtTokenProvider.getAccessTokenValidity()).willReturn(60_000L);

        // when
        AuthTokens tokens = authService.login(OAuthProvider.GOOGLE, request);

        // then
        assertThat(tokens.response().accessToken()).isEqualTo("access-token");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
        assertThat(tokens.response().tokenType()).isEqualTo("Bearer");
        verify(refreshTokenStore).save(1L, "refresh-token");
    }

    @Test
    @DisplayName("[리프레시 토큰이 아닌 토큰으로 재발급을 요청하면 예외가 발생한다]")
    void reissue_notRefreshToken_throwsUnauthorizedException() {
        // given
        given(jwtTokenProvider.isRefreshToken("access-token-misused")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.reissue("access-token-misused"))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("[저장된 리프레시 토큰과 일치하지 않으면 예외가 발생한다]")
    void reissue_mismatchStoredToken_throwsUnauthorizedException() {
        // given
        given(jwtTokenProvider.isRefreshToken("refresh-token")).willReturn(true);
        given(jwtTokenProvider.validateAndGetUserId("refresh-token")).willReturn(1L);
        given(refreshTokenStore.findByUserId(1L)).willReturn(Optional.of("different-token"));

        // when & then
        assertThatThrownBy(() -> authService.reissue("refresh-token"))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("[리프레시 토큰이 유효하면 새 토큰을 재발급한다]")
    void reissue_valid_issuesNewTokens() {
        // given
        User user = UserFixture.createUser();
        setUserId(user, 1L);

        given(jwtTokenProvider.isRefreshToken("refresh-token")).willReturn(true);
        given(jwtTokenProvider.validateAndGetUserId("refresh-token")).willReturn(1L);
        given(refreshTokenStore.findByUserId(1L)).willReturn(Optional.of("refresh-token"));
        given(userService.getById(1L)).willReturn(user);
        given(jwtTokenProvider.createAccessToken(anyLong(), any())).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(anyLong())).willReturn("new-refresh-token");
        given(jwtTokenProvider.getAccessTokenValidity()).willReturn(60_000L);

        // when
        AuthTokens tokens = authService.reissue("refresh-token");

        // then
        assertThat(tokens.response().accessToken()).isEqualTo("new-access-token");
        assertThat(tokens.refreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenStore).save(1L, "new-refresh-token");
    }

    @Test
    @DisplayName("[로그아웃하면 저장된 리프레시 토큰을 삭제한다]")
    void logout_deletesRefreshToken() {
        // when
        authService.logout(1L);

        // then
        verify(refreshTokenStore).delete(1L);
    }

    private void setUserId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
