package com.quantlab.common.util;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * "enum 상수 이름(대소문자 무시)으로 매칭해 변환, 실패 시 지정한 예외"라는
 * {@code of(String)} 정적 팩토리 패턴을 여러 enum(Grade, OAuthProvider 등)이
 * 각자 재구현하고 있어 공용 유틸로 추출했다.
 */
public final class EnumCodeMatcher {

    private EnumCodeMatcher() {
    }

    public static <E extends Enum<E>> E matchByName(
        Class<E> enumType, String rawValue, Supplier<? extends RuntimeException> onNotFound) {
        return Arrays.stream(enumType.getEnumConstants())
            .filter(constant -> constant.name().equalsIgnoreCase(rawValue))
            .findFirst()
            .orElseThrow(onNotFound);
    }
}
