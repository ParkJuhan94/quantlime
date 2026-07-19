package com.quantlab.user.dto.response;

public record UserMeResponse(
    String nickname,
    String email,
    String profileImageUrl
) {
}
