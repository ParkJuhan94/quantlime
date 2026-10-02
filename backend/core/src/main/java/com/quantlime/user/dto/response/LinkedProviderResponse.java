package com.quantlime.user.dto.response;

/** 내 계정에 연결된 소셜 로그인 제공자. primary는 가입에 사용한 계정(연결 해제 불가). */
public record LinkedProviderResponse(
    String provider,
    String label,
    boolean primary
) {
}
