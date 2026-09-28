package com.quantlime.infra.oauth;

import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.oauth.dto.NaverTokenResponse;
import com.quantlime.infra.oauth.dto.NaverUserInfoResponse;
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
 * {@code fetch}에 {@code @CircuitBreaker}/{@code @Bulkhead}("oauth-naver" 인스턴스)
 * 적용(2026-09-24) - 프로바이더별 분리 이유는 {@link GoogleOAuthClient} 클래스
 * 주석 참고.
 */
@Component
@RequiredArgsConstructor
public class NaverOAuthClient implements OAuthClient {

    private static final String DEFAULT_NICKNAME = "네이버사용자";

    private final RestClient oAuthRestClient;
    private final OAuthProperties properties;

    @Override
    public boolean supports(OAuthProvider provider) {
        return provider == OAuthProvider.NAVER;
    }

    @Override
    @CircuitBreaker(name = "oauth-naver", fallbackMethod = "fetchFallback")
    @Bulkhead(name = "oauth-naver")
    public OAuthUserInfo fetch(String code, String redirectUri) {
        try {
            OAuthProperties.Provider naver = properties.getNaver();

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "authorization_code");
            formData.add("client_id", naver.getClientId());
            formData.add("client_secret", naver.getClientSecret());
            formData.add("code", code);

            NaverTokenResponse tokenResponse = oAuthRestClient.post()
                .uri(naver.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(NaverTokenResponse.class);

            if (tokenResponse == null || tokenResponse.accessToken() == null) {
                throw new ExternalApiException(AuthErrorCode.OAUTH_USERINFO_FAILED);
            }

            NaverUserInfoResponse userInfo = oAuthRestClient.get()
                .uri(naver.getUserInfoUri())
                .header("authorization", "Bearer " + tokenResponse.accessToken())
                .retrieve()
                .body(NaverUserInfoResponse.class);

            if (userInfo == null || userInfo.response() == null) {
                throw new ExternalApiException(AuthErrorCode.OAUTH_USERINFO_FAILED);
            }

            NaverUserInfoResponse.NaverAccount account = userInfo.response();
            String nickname = StringUtils.hasText(account.nickname())
                ? account.nickname() : DEFAULT_NICKNAME;

            return new OAuthUserInfo(OAuthProvider.NAVER, account.id(),
                account.email(), nickname, account.profileImage());
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
