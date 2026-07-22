package com.quantlime.infra.oauth;

import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.oauth.dto.KakaoTokenResponse;
import com.quantlime.infra.oauth.dto.KakaoUserInfoResponse;
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
public class KakaoOAuthClient implements OAuthClient {

    private static final String DEFAULT_NICKNAME = "카카오사용자";

    private final RestClient oAuthRestClient;
    private final OAuthProperties properties;

    @Override
    public boolean supports(OAuthProvider provider) {
        return provider == OAuthProvider.KAKAO;
    }

    @Override
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
}
