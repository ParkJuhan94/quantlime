package com.quantlab.auth.controller;

import com.quantlab.auth.cookie.RefreshTokenCookieProvider;
import com.quantlab.auth.dto.request.SocialLoginRequest;
import com.quantlab.auth.dto.response.TokenResponse;
import com.quantlab.auth.exception.AuthErrorCode;
import com.quantlab.auth.jwt.JwtTokenProvider;
import com.quantlab.auth.resolver.LoginUser;
import com.quantlab.auth.service.AuthService;
import com.quantlab.auth.service.AuthTokens;
import com.quantlab.common.exception.UnauthorizedException;
import com.quantlab.user.domain.OAuthProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/login/{provider}")
    @Operation(
        summary = "소셜 로그인",
        description = "소셜 로그인 제공자의 인가 코드를 받아 로그인/회원가입 처리 후 토큰을 발급한다. "
            + "리프레시 토큰은 응답 바디가 아니라 httpOnly 쿠키로 내려온다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<TokenResponse> login(
        @PathVariable String provider,
        @Valid @RequestBody SocialLoginRequest request) {
        AuthTokens tokens = authService.login(OAuthProvider.of(provider), request);
        return withRefreshTokenCookie(tokens);
    }

    @PostMapping("/reissue")
    @Operation(
        summary = "토큰 재발급",
        description = "쿠키로 전달된 리프레시 토큰으로 액세스 토큰을 재발급한다(리프레시 토큰도 회전되어 쿠키가 갱신된다)"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<TokenResponse> reissue(
        @CookieValue(name = RefreshTokenCookieProvider.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new UnauthorizedException(AuthErrorCode.INVALID_TOKEN);
        }
        AuthTokens tokens = authService.reissue(refreshToken);
        return withRefreshTokenCookie(tokens);
    }

    @PostMapping("/logout")
    @Operation(
        summary = "로그아웃",
        description = "현재 사용자의 리프레시 토큰을 무효화하고 쿠키를 지운다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> logout(@LoginUser Long userId) {
        authService.logout(userId);
        ResponseCookie cleared = refreshTokenCookieProvider.clear();
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, cleared.toString())
            .build();
    }

    private ResponseEntity<TokenResponse> withRefreshTokenCookie(AuthTokens tokens) {
        ResponseCookie cookie = refreshTokenCookieProvider.create(
            tokens.refreshToken(), jwtTokenProvider.getRefreshTokenValidity());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(tokens.response());
    }
}
