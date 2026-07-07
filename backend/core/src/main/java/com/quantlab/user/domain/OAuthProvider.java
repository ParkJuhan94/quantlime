package com.quantlab.user.domain;

import com.quantlab.common.exception.ValidationException;
import com.quantlab.common.util.EnumCodeMatcher;
import com.quantlab.user.exception.UserErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OAuthProvider {

    GOOGLE("구글"),
    KAKAO("카카오"),
    NAVER("네이버");

    private final String label;

    public static OAuthProvider of(String value) {
        return EnumCodeMatcher.matchByName(OAuthProvider.class, value,
            () -> new ValidationException(UserErrorCode.INVALID_OAUTH_PROVIDER));
    }
}
