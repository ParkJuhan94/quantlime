package com.quantlime.auth.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
class RefreshTokenCookieProviderTest {

    private RefreshTokenCookieProvider provider(boolean secure) {
        RefreshTokenCookieProvider provider = new RefreshTokenCookieProvider();
        ReflectionTestUtils.setField(provider, "secure", secure);
        return provider;
    }

    @Test
    @DisplayName("[쿠키는 httpOnly + SameSite=Strict + /api/auth 경로로 발급되고 만료가 유효기간과 같다]")
    void create_hasSecurityAttributes() {
        // when
        ResponseCookie cookie = provider(false).create("token-value", 604_800_000L);

        // then
        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEqualTo("token-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMillis(604_800_000L));
    }

    @Test
    @DisplayName("[secure 플래그는 app.cookie.secure 설정을 따른다]")
    void create_secureFollowsConfig() {
        assertThat(provider(true).create("t", 1000L).isSecure()).isTrue();
        assertThat(provider(false).create("t", 1000L).isSecure()).isFalse();
    }

    @Test
    @DisplayName("[clear는 같은 이름/경로로 빈 값 + maxAge 0 쿠키를 만들어 브라우저가 즉시 지우게 한다]")
    void clear_expiresImmediately() {
        // when
        ResponseCookie cookie = provider(false).clear();

        // then
        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }
}
