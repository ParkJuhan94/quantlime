package com.quantlime.user.dto.response;

public record UserMeResponse(
    String nickname,
    String email,
    String profileImageUrl
) {
}
