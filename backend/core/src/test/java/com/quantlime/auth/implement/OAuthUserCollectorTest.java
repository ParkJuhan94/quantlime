package com.quantlime.auth.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.quantlime.auth.dto.OAuthUserInfo;
import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.oauth.OAuthClient;
import com.quantlime.infra.oauth.OAuthClientDispatcher;
import com.quantlime.infra.oauth.dto.OAuthProfile;
import com.quantlime.infra.oauth.exception.OAuthErrorCode;
import com.quantlime.user.domain.OAuthProvider;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OAuthUserCollectorTest {

    @Mock
    private OAuthClient client;

    @Test
    @DisplayName("[제공자 이름이 enum 전체와 일치해 모듈 클라이언트가 모두 지원한다]")
    void everyProviderIsSupportedByModuleClients() {
        // 모듈의 실제 클라이언트 3종 이름이 OAuthProvider enum과 어긋나면 여기서 잡힌다
        var dispatcher = new OAuthClientDispatcher(List.of(
            new com.quantlime.infra.oauth.GoogleOAuthClient(null, null),
            new com.quantlime.infra.oauth.KakaoOAuthClient(null, null),
            new com.quantlime.infra.oauth.NaverOAuthClient(null, null)));

        for (OAuthProvider provider : OAuthProvider.values()) {
            assertThat(dispatcher.supports(provider.name())).as(provider.name()).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(OAuthProvider.class)
    @DisplayName("[모듈 프로필을 도메인 OAuthUserInfo로 변환한다]")
    void fetch_mapsProfile(OAuthProvider provider) {
        given(client.supports(provider.name())).willReturn(true);
        given(client.fetch("code", "uri"))
            .willReturn(new OAuthProfile(provider.name(), "pid", "a@b.c", "닉", "img"));
        var collector = new OAuthUserCollector(new OAuthClientDispatcher(List.of(client)));

        OAuthUserInfo info = collector.fetch(provider, "code", "uri");

        assertThat(info).isEqualTo(new OAuthUserInfo(provider, "pid", "a@b.c", "닉", "img"));
    }

    @Test
    @DisplayName("[지원 클라이언트가 없으면 UNSUPPORTED_PROVIDER로 변환한다]")
    void fetch_unsupported_throwsValidation() {
        var collector = new OAuthUserCollector(new OAuthClientDispatcher(List.of(client)));

        assertThatThrownBy(() -> collector.fetch(OAuthProvider.GOOGLE, "c", "u"))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("[모듈의 연동 실패는 AU_004로 변환되어 클라이언트 계약이 유지된다]")
    void fetch_moduleFailure_translatedToAuthErrorCode() {
        given(client.supports(anyString())).willReturn(true);
        given(client.fetch(any(), any()))
            .willThrow(new ExternalApiException(OAuthErrorCode.OAUTH_USERINFO_FAILED));
        var collector = new OAuthUserCollector(new OAuthClientDispatcher(List.of(client)));

        assertThatThrownBy(() -> collector.fetch(OAuthProvider.KAKAO, "c", "u"))
            .isInstanceOfSatisfying(ExternalApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(AuthErrorCode.OAUTH_USERINFO_FAILED.getCode()));
    }
}
