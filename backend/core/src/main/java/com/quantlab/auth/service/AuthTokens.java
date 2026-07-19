package com.quantlab.auth.service;

import com.quantlab.auth.dto.response.TokenResponse;

/**
 * AuthService와 컨트롤러 계층 사이의 내부 전달용 객체. 리프레시 토큰
 * 원본 문자열은 컨트롤러가 httpOnly 쿠키를 만들 때만 필요하고, 외부에
 * 노출되는 {@link TokenResponse}에는 더 이상 담기지 않는다(2026-07-15
 * 세션 - 쿠키 이전).
 */
public record AuthTokens(TokenResponse response, String refreshToken) {
}
