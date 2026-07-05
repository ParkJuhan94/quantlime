package com.quantlab.infra.oauth.dto;

import com.quantlab.user.domain.OAuthProvider;

public record OAuthUserInfo(
    OAuthProvider provider,
    String providerId,
    String email,
    String nickname,
    String profileImageUrl
) {
}
