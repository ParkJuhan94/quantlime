package com.quantlime.auth.implement;

import com.quantlime.auth.dto.OAuthUserInfo;
import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.oauth.OAuthClientDispatcher;
import com.quantlime.infra.oauth.dto.OAuthProfile;
import com.quantlime.user.domain.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * oauth 모듈(외부 제공자 연동)과 도메인의 경계. 모듈은 도메인 타입({@link OAuthProvider},
 * {@link AuthErrorCode})을 모르므로 여기서 제공자 이름을 enum으로, 연동 실패를 인증 에러 코드로
 * 변환한다 - 클라이언트에 노출되는 에러 코드(AU_003/AU_004)는 이 변환으로 유지된다.
 */
@Component
@RequiredArgsConstructor
public class OAuthUserCollector {

    private final OAuthClientDispatcher dispatcher;

    public OAuthUserInfo fetch(OAuthProvider provider, String code, String redirectUri) {
        if (!dispatcher.supports(provider.name())) {
            throw new ValidationException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        }
        OAuthProfile profile;
        try {
            profile = dispatcher.fetch(provider.name(), code, redirectUri);
        } catch (ExternalApiException e) {
            throw new ExternalApiException(AuthErrorCode.OAUTH_USERINFO_FAILED, e);
        }
        return new OAuthUserInfo(
            OAuthProvider.valueOf(profile.provider()),
            profile.providerId(),
            profile.email(),
            profile.nickname(),
            profile.profileImageUrl());
    }
}
