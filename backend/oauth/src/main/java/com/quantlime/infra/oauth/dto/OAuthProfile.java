package com.quantlime.infra.oauth.dto;

import org.springframework.lang.Nullable;

public record OAuthProfile(
    String provider,
    String providerId,
    @Nullable String email,
    String nickname,
    @Nullable String profileImageUrl
) {
}
