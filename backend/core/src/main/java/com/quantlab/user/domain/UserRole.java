package com.quantlab.user.domain;

import com.quantlab.common.exception.ValidationException;
import com.quantlab.user.exception.UserErrorCode;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {

    USER("일반 사용자"),
    ADMIN("관리자");

    private final String label;

    public static UserRole of(String label) {
        return Arrays.stream(values())
            .filter(role -> role.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new ValidationException(UserErrorCode.INVALID_USER_ROLE));
    }
}
