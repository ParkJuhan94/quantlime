package com.quantlab.infra.oauth;

import com.quantlab.infra.oauth.dto.OAuthUserInfo;
import com.quantlab.user.domain.OAuthProvider;

public interface OAuthClient {

    boolean supports(OAuthProvider provider);

    OAuthUserInfo fetch(String code, String redirectUri);
}
