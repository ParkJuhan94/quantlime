package com.quantlab.auth.dto.response;

public record TokenResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long accessTokenExpiresIn
) {
}
