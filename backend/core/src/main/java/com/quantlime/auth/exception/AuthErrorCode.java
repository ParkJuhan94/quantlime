package com.quantlime.auth.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_TOKEN("유효하지 않은 토큰입니다.", "AU_000"),
    EXPIRED_TOKEN("만료된 토큰입니다.", "AU_001"),
    MISMATCH_REFRESH_TOKEN("일치하지 않는 리프레시 토큰입니다.", "AU_002"),
    UNSUPPORTED_PROVIDER("지원하지 않는 소셜 로그인 제공자입니다.", "AU_003"),
    OAUTH_USERINFO_FAILED("소셜 로그인 사용자 정보 조회에 실패했습니다.", "AU_004");

    private final String message;
    private final String code;
}
