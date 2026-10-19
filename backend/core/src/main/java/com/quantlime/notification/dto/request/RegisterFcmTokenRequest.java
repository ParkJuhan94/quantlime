package com.quantlime.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RegisterFcmTokenRequest(
    @Schema(description = "Firebase Cloud Messaging 기기 토큰", example = "fcm-device-token")
    @NotBlank(message = "FCM 토큰은 필수입니다.") String token,
    @Schema(description = "기기 식별용 정보(선택)", example = "Chrome 130 / macOS", nullable = true)
    String deviceInfo
) {
}
