package com.quantlab.auth.dto.response;

// 리프레시 토큰은 더 이상 응답 바디에 담지 않는다 - httpOnly 쿠키로만
// 내려준다(RefreshTokenCookieProvider 참고, 2026-07-15 세션).
public record TokenResponse(
    String accessToken,
    String tokenType,
    long accessTokenExpiresIn
) {
}
