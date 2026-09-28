package com.quantlime.infra.oauth;

import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.oauth.dto.KakaoTokenResponse;
import com.quantlime.infra.oauth.dto.KakaoUserInfoResponse;
import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.user.domain.OAuthProvider;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * {@code fetch}에 {@code @CircuitBreaker}/{@code @Bulkhead}("oauth-kakao" 인스턴스)
 * 적용(2026-09-24) - 프로바이더별 분리 이유는 {@link GoogleOAuthClient} 클래스
 * 주석 참고.
 */
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient implements OAuthClient {

    private static final String DEFAULT_NICKNAME = "카카오사용자";

    private final RestClient oAuthRestClient;
    private final OAuthProperties properties;

    @Override
    public boolean supports(OAuthProvider provider) {
        return provider == OAuthProvider.KAKAO;
    }

    @Override
    @CircuitBreaker(name = "oauth-kakao", fallbackMethod = "fetchFallback")
    @Bulkhead(name = "oauth-kakao")
    public OAuthUserInfo fetch(String code, String redirectUri) {
        try {
            OAuthProperties.Provider kakao = properties.getKakao();

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "authorization_code");
            formData.add("client_id", kakao.getClientId());
            formData.add("client_secret", kakao.getClientSecret());
            formData.add("redirect_uri", redirectUri);
            formData.add("code", code);

            KakaoTokenResponse tokenResponse = oAuthRestClient.post()
                .uri(kakao.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(KakaoTokenResponse.class);

            if (tokenResponse == null || tokenResponse.accessToken() == null) {
                throw new ExternalApiException(AuthErrorCode.OAUTH_USERINFO_FAILED);
            }

            KakaoUserInfoResponse userInfo = oAuthRestClient.get()
                .uri(kakao.getUserInfoUri())
                .header("authorization", "Bearer " + tokenResponse.accessToken())
                .retrieve()
                .body(KakaoUserInfoResponse.class);

            if (userInfo == null) {
                throw new ExternalApiException(AuthErrorCode.OAUTH_USERINFO_FAILED);
            }

            String email = userInfo.kakaoAccount() != null
                ? userInfo.kakaoAccount().email() : null;
            String nickname = userInfo.kakaoAccount() != null
                && userInfo.kakaoAccount().profile() != null
                ? userInfo.kakaoAccount().profile().nickname() : null;
            String profileImageUrl = userInfo.kakaoAccount() != null
                && userInfo.kakaoAccount().profile() != null
                ? userInfo.kakaoAccount().profile().profileImageUrl() : null;

            return new OAuthUserInfo(OAuthProvider.KAKAO, String.valueOf(userInfo.id()),
                email, StringUtils.hasText(nickname) ? nickname : DEFAULT_NICKNAME, profileImageUrl);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException(AuthErrorCode.OAUTH_USERINFO_FAILED, e);
        }
    }

    // GoogleOAuthClient.fetchFallback와 동일한 이유 - 서킷 open 시 위 try/catch를
    // 건너뛰고 곧장 CallNotPermittedException이 던져지므로 여기서 통일한다.
    @SuppressWarnings("unused")
    private OAuthUserInfo fetchFallback(String code, String redirectUri, Throwable t) {
        if (t instanceof ExternalApiException e) {
            throw e;
        }
        throw new ExternalApiException(AuthErrorCode.OAUTH_USERINFO_FAILED, t);
    }
}
