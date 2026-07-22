package com.quantlime.infra.oauth;

import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.oauth.dto.GoogleTokenResponse;
import com.quantlime.infra.oauth.dto.GoogleUserInfoResponse;
import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.user.domain.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

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
}
