package com.quantlime.auth.jwt;

import com.quantlime.common.exception.UnauthorizedException;
import com.quantlime.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class JwtTokenProviderTest {

    private final JwtProperties jwtProperties = new JwtProperties(
        "test-jwt-secret-key-for-unit-test-must-be-long-enough-1234567890",
        60_000L,
        604_800_000L
    );
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);

    @Test
    @DisplayName("[액세스 토큰 발급 후 검증하면 사용자 ID와 권한을 얻는다]")
    void createAccessToken_validate_returnsUserIdAndRole() {
        // given
        Long userId = 1L;

        // when
        String accessToken = jwtTokenProvider.createAccessToken(userId, UserRole.USER);

        // then
        assertThat(jwtTokenProvider.validateAndGetUserId(accessToken)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getRole(accessToken)).isEqualTo(UserRole.USER);
        assertThat(jwtTokenProvider.isRefreshToken(accessToken)).isFalse();
    }

    @Test
    @DisplayName("[리프레시 토큰은 isRefreshToken이 true를 반환한다]")
    void createRefreshToken_isRefreshToken_returnsTrue() {
        // given
        Long userId = 1L;

        // when
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        // then
        assertThat(jwtTokenProvider.validateAndGetUserId(refreshToken)).isEqualTo(userId);
        assertThat(jwtTokenProvider.isRefreshToken(refreshToken)).isTrue();
        assertThat(jwtTokenProvider.getRole(refreshToken)).isNull();
    }

    @Test
    @DisplayName("[형식이 올바르지 않은 토큰을 검증하면 예외가 발생한다]")
    void validate_malformedToken_throwsUnauthorizedException() {
        // given
        String malformedToken = "this-is-not-a-jwt";

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateAndGetUserId(malformedToken))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("[만료된 토큰을 검증하면 예외가 발생한다]")
    void validate_expiredToken_throwsUnauthorizedException() {
        // given
        JwtProperties expiredJwtProperties = new JwtProperties(
            jwtProperties.getSecret(), -1_000L, -1_000L);
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(expiredJwtProperties);
        String expiredToken = expiredTokenProvider.createAccessToken(1L, UserRole.USER);

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateAndGetUserId(expiredToken))
            .isInstanceOf(UnauthorizedException.class);
    }
}
