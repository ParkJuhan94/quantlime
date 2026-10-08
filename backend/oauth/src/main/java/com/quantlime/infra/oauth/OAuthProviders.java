package com.quantlime.infra.oauth;

/**
 * 모듈이 식별하는 제공자 이름. core의 {@code OAuthProvider} enum 이름과 일치해야 하며,
 * 어긋나지 않도록 core 쪽 테스트가 enum 전체를 이 모듈의 클라이언트에 대조한다.
 */
public final class OAuthProviders {

    public static final String GOOGLE = "GOOGLE";
    public static final String KAKAO = "KAKAO";
    public static final String NAVER = "NAVER";

    private OAuthProviders() {
    }
}
