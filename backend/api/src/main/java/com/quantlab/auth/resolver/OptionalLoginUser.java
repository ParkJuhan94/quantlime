package com.quantlab.auth.resolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link LoginUser}와 달리 인증이 없어도 예외를 던지지 않고 null을
 * 반환한다 - 피드 글 목록처럼 "누구나 볼 수 있지만 로그인했으면
 * 개인화(좋아요 여부 등)하고 싶은" 공개 엔드포인트 전용.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalLoginUser {
}
