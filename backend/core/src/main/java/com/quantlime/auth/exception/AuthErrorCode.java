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
    OAUTH_USERINFO_FAILED("소셜 로그인 사용자 정보 조회에 실패했습니다.", "AU_004"),
    SOCIAL_ACCOUNT_IN_USE("이미 다른 계정에 연결된 소셜 계정입니다.", "AU_005"),
    PROVIDER_ALREADY_LINKED("이미 같은 제공자의 다른 소셜 계정이 연결되어 있습니다.", "AU_006"),
    CANNOT_UNLINK_PRIMARY("가입에 사용한 소셜 계정은 연결 해제할 수 없습니다.", "AU_007"),
    SOCIAL_ACCOUNT_NOT_LINKED("연결되어 있지 않은 소셜 계정입니다.", "AU_008");

    private final String message;
    private final String code;
}
