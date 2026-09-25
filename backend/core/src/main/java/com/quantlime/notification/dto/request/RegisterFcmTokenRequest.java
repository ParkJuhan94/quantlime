package com.quantlime.notification.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RegisterFcmTokenRequest(
    @NotBlank(message = "FCM 토큰은 필수입니다.") String token,
    String deviceInfo
) {
}
