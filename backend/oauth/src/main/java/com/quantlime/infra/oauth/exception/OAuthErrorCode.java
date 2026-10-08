package com.quantlime.infra.oauth.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 소셜 로그인 제공자 연동 실패 코드. 클라이언트에게 노출되는 코드(AU_004 등)는
 * core의 어댑터가 {@code AuthErrorCode}로 변환해 유지한다.
 */
@Getter
@RequiredArgsConstructor
public enum OAuthErrorCode implements ErrorCode {

    OAUTH_USERINFO_FAILED("소셜 로그인 사용자 정보 조회에 실패했습니다.", "OAUTH_000");

    private final String message;
    private final String code;
}
