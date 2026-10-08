package com.quantlime.infra.oauth.dto;

public record OAuthProfile(
    String provider,
    String providerId,
    String email,
    String nickname,
    String profileImageUrl
) {
}
