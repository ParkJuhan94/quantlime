package com.quantlime.auth.dto;

import com.quantlime.user.domain.OAuthProvider;

public record OAuthUserInfo(
    OAuthProvider provider,
    String providerId,
    String email,
    String nickname,
    String profileImageUrl
) {
}
