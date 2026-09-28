package com.quantlime.infra.oauth;

import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.oauth.dto.GoogleTokenResponse;
import com.quantlime.infra.oauth.dto.GoogleUserInfoResponse;
import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.user.domain.OAuthProvider;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * {@code fetch}에 {@code @CircuitBreaker}/{@code @Bulkhead}("oauth-google" 인스턴스)
 * 적용(2026-09-24) - 구글/카카오/네이버가 RestClient 빈은 공유하지만 인스턴스는
 * 프로바이더별로 분리했다. 하나로 묶으면 카카오 OAuth 서버 장애가 서킷을 열어
 * 구글/네이버 로그인까지 함께 막는 교차 영향이 생기기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class GoogleOAuthClient implements OAuthClient {

    private final RestClient oAuthRestClient;
    private final OAuthProperties properties;

    @Override
    public boolean supports(OAuthProvider provider) {
        return provider == OAuthProvider.GOOGLE;
    }

    @Override
    @CircuitBreaker(name = "oauth-google", fallbackMethod = "fetchFallback")
    @Bulkhead(name = "oauth-google")
    public OAuthUserInfo fetch(String code, String redirectUri) {
        try {
            OAuthProperties.Provider google = properties.getGoogle();

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "authorization_code");
            formData.add("client_id", google.getClientId());
            formData.add("client_secret", google.getClientSecret());
            formData.add("redirect_uri", redirectUri);
            formData.add("code", code);

            GoogleTokenResponse tokenResponse = oAuthRestClient.post()
                .uri(google.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(GoogleTokenResponse.class);

            if (tokenResponse == null || tokenResponse.accessToken() == null) {
                throw new ExternalApiException(AuthErrorCode.OAUTH_USERINFO_FAILED);
            }

            GoogleUserInfoResponse userInfo = oAuthRestClient.get()
                .uri(google.getUserInfoUri())
                .header("authorization", "Bearer " + tokenResponse.accessToken())
                .retrieve()
                .body(GoogleUserInfoResponse.class);

            if (userInfo == null) {
                throw new ExternalApiException(AuthErrorCode.OAUTH_USERINFO_FAILED);
            }

            return new OAuthUserInfo(OAuthProvider.GOOGLE, userInfo.sub(),
                userInfo.email(), userInfo.name(), userInfo.picture());
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException(AuthErrorCode.OAUTH_USERINFO_FAILED, e);
        }
    }

    // 서킷 open 시 resilience4j가 메서드 본문(위 try/catch)을 건너뛰고 곧장
    // CallNotPermittedException을 던지므로, 이 폴백이 없으면 그 예외가 그대로
    // 전파돼 GlobalExceptionHandler의 catch-all(500 "서버 내부 오류")로 새고
    // 만다 - 실패 원인이 외부 API인데 내 서버 탓처럼 보이는 문제.
    // ExternalApiException으로 통일해 기존 503 응답 경로를 그대로 타게 한다.
    @SuppressWarnings("unused")
    private OAuthUserInfo fetchFallback(String code, String redirectUri, Throwable t) {
        if (t instanceof ExternalApiException e) {
            throw e;
        }
        throw new ExternalApiException(AuthErrorCode.OAUTH_USERINFO_FAILED, t);
    }
}
