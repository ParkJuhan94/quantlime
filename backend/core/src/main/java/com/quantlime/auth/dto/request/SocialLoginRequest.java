package com.quantlime.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(
    @Schema(description = "소셜 로그인 인가 코드(OAuth authorization code)", example = "4/0AX4XfWh...")
    @NotBlank(message = "인가 코드는 필수입니다.") String code,
    @Schema(description = "인가 코드를 받을 때 사용한 리다이렉트 URI(OAuth 앱 설정과 일치해야 함)", example = "http://localhost:3001/oauth/callback")
    @NotBlank(message = "리다이렉트 URI는 필수입니다.") String redirectUri
) {
}
