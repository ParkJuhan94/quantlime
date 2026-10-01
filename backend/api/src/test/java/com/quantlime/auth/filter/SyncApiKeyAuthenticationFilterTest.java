package com.quantlime.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quantlime.infra.sync.SyncProperties;
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
class SyncApiKeyAuthenticationFilterTest {

    private static final String PROTECTED_PATH = "/api/admin/feed/transcripts/import";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(String configuredKey, String uri, String providedKey) throws Exception {
        SyncProperties properties = mock(SyncProperties.class);
        when(properties.getApiKey()).thenReturn(configuredKey);
        SyncApiKeyAuthenticationFilter filter = new SyncApiKeyAuthenticationFilter(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        if (providedKey != null) {
            request.addHeader("X-Sync-Api-Key", providedKey);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("체인은 항상 계속 진행돼야 한다").isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("[키가 일치하면 대상 경로에 한해 ROLE_ADMIN 인증을 채운다]")
    void matchingKey_onProtectedPath_authenticatesAsAdmin() throws Exception {
        // when
        Authentication authentication = run("secret", PROTECTED_PATH, "secret");

        // then
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo("local-sync");
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("[키가 틀리면 인증하지 않는다]")
    void wrongKey_doesNotAuthenticate() throws Exception {
        assertThat(run("secret", PROTECTED_PATH, "wrong")).isNull();
    }

    @Test
    @DisplayName("[키 헤더가 없으면 인증하지 않는다]")
    void missingHeader_doesNotAuthenticate() throws Exception {
        assertThat(run("secret", PROTECTED_PATH, null)).isNull();
    }

    @Test
    @DisplayName("[키가 맞아도 대상 경로가 아니면 인증하지 않는다 - 키 유출 시 영향 범위를 한 경로로 제한]")
    void matchingKey_onOtherPath_doesNotAuthenticate() throws Exception {
        assertThat(run("secret", "/api/admin/feed/channels", "secret")).isNull();
    }

    @Test
    @DisplayName("[서버 키가 비어 있으면(기본값) 헤더가 있어도 기능이 비활성화된다]")
    void blankConfiguredKey_disablesFilter() throws Exception {
        assertThat(run("", PROTECTED_PATH, "")).isNull();
        assertThat(run("", PROTECTED_PATH, "anything")).isNull();
    }
}
