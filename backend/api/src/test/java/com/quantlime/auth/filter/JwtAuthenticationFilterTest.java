package com.quantlime.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.auth.jwt.JwtProperties;
import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.user.domain.UserRole;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Tag("unit")
class JwtAuthenticationFilterTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(new JwtProperties(
        "test-jwt-secret-key-for-unit-test-must-be-long-enough-1234567890", 60_000L, 604_800_000L));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain run(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    @DisplayName("[유효한 액세스 토큰이면 사용자 ID를 principal로, 권한을 ROLE_ 접두사로 인증을 채운다]")
    void validAccessToken_setsAuthentication() throws Exception {
        // given
        String token = jwtTokenProvider.createAccessToken(42L, UserRole.ADMIN);

        // when
        MockFilterChain chain = run("Bearer " + token);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(42L);
        assertThat(authentication.getAuthorities()).extracting(Object::toString).isEqualTo(List.of("ROLE_ADMIN"));
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("[Authorization 헤더가 없으면 인증 없이 체인을 계속 진행한다]")
    void noHeader_continuesUnauthenticated() throws Exception {
        // when
        MockFilterChain chain = run(null);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("[Bearer 접두사가 아닌 헤더는 무시한다]")
    void nonBearerHeader_isIgnored() throws Exception {
        // given
        String token = jwtTokenProvider.createAccessToken(1L, UserRole.USER);

        // when
        run("Basic " + token);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("[리프레시 토큰을 Authorization 헤더에 실어도 인증되지 않는다 - 14일짜리 토큰의 액세스 토큰 오용 차단]")
    void refreshToken_isRejected() throws Exception {
        // given
        String refreshToken = jwtTokenProvider.createRefreshToken(1L);

        // when
        MockFilterChain chain = run("Bearer " + refreshToken);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("[위조/깨진 토큰이면 인증 없이 체인을 계속 진행한다(401 판정은 뒤 단계 몫)]")
    void invalidToken_continuesUnauthenticated() throws Exception {
        // when
        MockFilterChain chain = run("Bearer not-a-jwt");

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("[다른 비밀키로 서명된 토큰은 인증되지 않는다]")
    void tokenSignedWithOtherSecret_isRejected() throws Exception {
        // given
        JwtTokenProvider other = new JwtTokenProvider(new JwtProperties(
            "another-secret-key-for-unit-test-must-be-long-enough-0987654321", 60_000L, 604_800_000L));

        // when
        run("Bearer " + other.createAccessToken(1L, UserRole.USER));

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
