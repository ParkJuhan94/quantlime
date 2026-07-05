package com.quantlab.user.domain;

import com.quantlab.common.exception.ValidationException;
import com.quantlab.user.exception.UserErrorCode;
import java.util.Arrays;
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
        return Arrays.stream(values())
            .filter(provider -> provider.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new ValidationException(UserErrorCode.INVALID_OAUTH_PROVIDER));
    }
}
