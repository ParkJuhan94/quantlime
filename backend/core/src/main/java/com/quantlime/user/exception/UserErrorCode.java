package com.quantlime.user.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    NOT_FOUND_USER("해당 사용자를 찾을 수 없습니다.", "US_000"),
    INVALID_OAUTH_PROVIDER("올바른 소셜 로그인 제공자를 입력해주세요.", "US_001"),
    INVALID_USER_ROLE("올바른 사용자 권한을 입력해주세요.", "US_002");

    private final String message;
    private final String code;
}
