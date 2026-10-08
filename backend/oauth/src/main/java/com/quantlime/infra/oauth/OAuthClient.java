package com.quantlime.infra.oauth;

import com.quantlime.infra.oauth.dto.OAuthProfile;

public interface OAuthClient {

    boolean supports(String provider);

    OAuthProfile fetch(String code, String redirectUri);
}
