package com.quantlime.infra.oauth.dto;

import com.quantlime.user.domain.OAuthProvider;

public record OAuthUserInfo(
    OAuthProvider provider,
    String providerId,
    String email,
    String nickname,
    String profileImageUrl
) {
}
