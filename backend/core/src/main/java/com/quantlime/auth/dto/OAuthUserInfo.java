package com.quantlime.auth.dto;

import com.quantlime.user.domain.OAuthProvider;
import org.springframework.lang.Nullable;

public record OAuthUserInfo(
    OAuthProvider provider,
    String providerId,
    @Nullable String email,
    String nickname,
    @Nullable String profileImageUrl
) {
}
