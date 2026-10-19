package com.quantlime.user.dto.response;

public record UserMeResponse(
    String nickname,
    String email,
    String profileImageUrl,
    // USER / ADMIN - 프론트가 관리자 메뉴·화면 노출 여부를 정하는 데만 쓴다(실제 권한은 백엔드가 JWT로 판정)
    String role
) {
}
