package com.quantlime.infra.oauth;

import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.user.domain.OAuthProvider;

public interface OAuthClient {

    boolean supports(OAuthProvider provider);

    OAuthUserInfo fetch(String code, String redirectUri);
}
