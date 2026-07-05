package com.quantlab.auth.controller;

import com.quantlab.auth.dto.request.ReissueRequest;
import com.quantlab.auth.dto.request.SocialLoginRequest;
import com.quantlab.auth.dto.response.TokenResponse;
import com.quantlab.auth.resolver.LoginUser;
import com.quantlab.auth.service.AuthService;
import com.quantlab.user.domain.OAuthProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/login/{provider}")
    @Operation(
        summary = "소셜 로그인",
        description = "소셜 로그인 제공자의 인가 코드를 받아 로그인/회원가입 처리 후 토큰을 발급한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<TokenResponse> login(
        @PathVariable String provider,
        @Valid @RequestBody SocialLoginRequest request) {
        TokenResponse response = authService.login(OAuthProvider.of(provider), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reissue")
    @Operation(
        summary = "토큰 재발급",
        description = "리프레시 토큰으로 액세스/리프레시 토큰을 재발급한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<TokenResponse> reissue(
        @Valid @RequestBody ReissueRequest request) {
        TokenResponse response = authService.reissue(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(
        summary = "로그아웃",
        description = "현재 사용자의 리프레시 토큰을 무효화한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> logout(@LoginUser Long userId) {
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}
