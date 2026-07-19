package com.quantlab.auth.cookie;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰을 httpOnly 쿠키로 내려준다(2026-07-15 세션 - 이전엔
 * 응답 바디로 내려 localStorage에 저장했는데, XSS로 스크립트가 실행되면
 * 액세스 토큰뿐 아니라 14일짜리 리프레시 토큰까지 그대로 읽혀 탈취
 * 범위가 컸다).
 *
 * <p>secure 플래그는 배포 환경의 TLS 적용 여부에 따라 갈린다 - 로컬
 * 개발(HTTP)과 아직 TLS 미적용 상태인 현재 프로덕션(docker-compose.prod.yml
 * + nginx, 순수 HTTP)에서는 secure=true인 쿠키를 브라우저가 아예
 * 저장하지 않으므로, 환경변수(COOKIE_SECURE)로 제어하고 기본값은 false로
 * 둔다. HTTPS 적용 후 반드시 true로 전환할 것(docs/DEPLOYMENT.md 참고).
 *
 * <p>sameSite=Strict를 쓴다 - 이 쿠키는 프론트가 같은 사이트에서 보내는
 * fetch/axios 요청(재발급·로그아웃)에만 필요하고, 외부 사이트로부터의
 * top-level 내비게이션에 실려갈 이유가 없다(OAuth 리다이렉트는 이
 * 쿠키와 무관 - 인가 코드 교환은 별도 로그인 API가 응답 바디로 토큰을
 * 내려준다). Strict로 묶어두면 CSRF(위조 요청이 쿠키를 얹어 재발급·
 * 로그아웃을 트리거하는 것)가 원천 차단된다 - 이 프로젝트는 CSRF 토큰
 * 메커니즘이 따로 없어(Bearer 헤더 기반이라 필요 없었음) 쿠키 자체의
 * SameSite 속성이 유일한 방어선이다.
 */
@Component
public class RefreshTokenCookieProvider {

    public static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    @Value("${app.cookie.secure:false}")
    private boolean secure;

    public ResponseCookie create(String refreshToken, long validityMs) {
        return baseCookie(refreshToken)
            .maxAge(Duration.ofMillis(validityMs))
            .build();
    }

    /** 로그아웃 시 브라우저가 쿠키를 즉시 지우도록 maxAge=0으로 재발급한다. */
    public ResponseCookie clear() {
        return baseCookie("")
            .maxAge(Duration.ZERO)
            .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path(COOKIE_PATH);
    }
}
