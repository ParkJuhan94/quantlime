package com.quantlime.auth.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quantlime.common.exception.UnauthorizedException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@Tag("unit")
class LoginUserArgumentResolversTest {

    private final LoginUserArgumentResolver required = new LoginUserArgumentResolver();
    private final OptionalLoginUserArgumentResolver optional = new OptionalLoginUserArgumentResolver();

    @SuppressWarnings("unused")
    void handler(@LoginUser Long loginUser, @OptionalLoginUser Long optionalUser,
                 @LoginUser String wrongType, Long plain) {
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MethodParameter param(int index) throws Exception {
        return new MethodParameter(
            LoginUserArgumentResolversTest.class.getDeclaredMethod("handler",
                Long.class, Long.class, String.class, Long.class), index);
    }

    private void authenticateAs(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    @DisplayName("[supportsParameter는 어노테이션이 있고 타입이 Long일 때만 true]")
    void supportsParameter_requiresAnnotationAndLongType() throws Exception {
        assertThat(required.supportsParameter(param(0))).isTrue();
        assertThat(required.supportsParameter(param(1))).isFalse();
        assertThat(required.supportsParameter(param(2))).isFalse();
        assertThat(required.supportsParameter(param(3))).isFalse();

        assertThat(optional.supportsParameter(param(1))).isTrue();
        assertThat(optional.supportsParameter(param(0))).isFalse();
        assertThat(optional.supportsParameter(param(3))).isFalse();
    }

    @Test
    @DisplayName("[LoginUser: 인증된 Long principal을 그대로 반환한다]")
    void required_authenticated_returnsUserId() throws Exception {
        authenticateAs(7L);
        assertThat(required.resolveArgument(param(0), null, null, null)).isEqualTo(7L);
    }

    @Test
    @DisplayName("[LoginUser: 인증이 없거나 principal이 Long이 아니면 UnauthorizedException]")
    void required_unauthenticated_throws() throws Exception {
        assertThatThrownBy(() -> required.resolveArgument(param(0), null, null, null))
            .isInstanceOf(UnauthorizedException.class);

        authenticateAs("anonymousUser");
        assertThatThrownBy(() -> required.resolveArgument(param(0), null, null, null))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("[OptionalLoginUser: 인증된 경우 userId, 아니면 예외 없이 null]")
    void optional_returnsUserIdOrNull() throws Exception {
        assertThat(optional.resolveArgument(param(1), null, null, null)).isNull();

        authenticateAs("anonymousUser");
        assertThat(optional.resolveArgument(param(1), null, null, null)).isNull();

        authenticateAs(9L);
        assertThat(optional.resolveArgument(param(1), null, null, null)).isEqualTo(9L);
    }
}
