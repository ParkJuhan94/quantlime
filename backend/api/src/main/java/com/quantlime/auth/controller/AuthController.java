package com.quantlime.auth.controller;

import com.quantlime.auth.cookie.RefreshTokenCookieProvider;
import com.quantlime.auth.dto.request.SocialLoginRequest;
import com.quantlime.auth.dto.response.TokenResponse;
import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.auth.resolver.LoginUser;
import com.quantlime.auth.service.AuthService;
import com.quantlime.auth.service.AuthTokens;
import com.quantlime.common.exception.UnauthorizedException;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.dto.response.LinkedProviderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    @PostMapping("/link/{provider}")
    @Operation(
        summary = "소셜 계정 추가 연결",
        description = "로그인한 사용자가 다른 소셜 계정을 추가로 연결한다. 해당 소셜 계정의 인가 코드를 교환해 소유를 확인한 "
            + "뒤에만 연결하며(이메일 일치로 자동 병합하지 않음), 이미 다른 사용자에게 속한 계정(AU_005)이거나 같은 "
            + "제공자의 다른 계정이 이미 연결돼 있으면(AU_006) 400. 이미 연결된 같은 계정이면 조용히 성공한다"
    )
    public ResponseEntity<Void> link(
        @LoginUser Long userId,
        @PathVariable String provider,
        @Valid @RequestBody SocialLoginRequest request) {
        authService.link(userId, OAuthProvider.of(provider), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/link/{provider}")
    @Operation(summary = "소셜 계정 연결 해제", description = "가입에 사용한 계정은 해제할 수 없다(AU_007)")
    public ResponseEntity<Void> unlink(@LoginUser Long userId, @PathVariable String provider) {
        authService.unlink(userId, OAuthProvider.of(provider));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/linked-providers")
    @Operation(summary = "연결된 소셜 계정 목록", description = "가입에 사용한 계정(primary=true)과 추가로 연결한 계정")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<LinkedProviderResponse>> linkedProviders(@LoginUser Long userId) {
        return ResponseEntity.ok(authService.linkedProviders(userId));
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
