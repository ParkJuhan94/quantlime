package com.quantlime.infra.oauth;

import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.oauth.dto.NaverTokenResponse;
import com.quantlime.infra.oauth.dto.NaverUserInfoResponse;
import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.user.domain.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

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
}
