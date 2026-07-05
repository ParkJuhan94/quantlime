package com.quantlab.infra.oauth;

import com.quantlab.auth.exception.AuthErrorCode;
import com.quantlab.common.exception.ExternalApiException;
import com.quantlab.infra.oauth.dto.NaverTokenResponse;
import com.quantlab.infra.oauth.dto.NaverUserInfoResponse;
import com.quantlab.infra.oauth.dto.OAuthUserInfo;
import com.quantlab.user.domain.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class NaverOAuthClient implements OAuthClient {

    private final RestClient oAuthRestClient;
    private final OAuthProperties properties;

    @Override
    public boolean supports(OAuthProvider provider) {
        return provider == OAuthProvider.NAVER;
    }

    @Override
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
            return new OAuthUserInfo(OAuthProvider.NAVER, account.id(),
                account.email(), account.nickname(), account.profileImage());
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException(AuthErrorCode.OAUTH_USERINFO_FAILED, e);
        }
    }
}
