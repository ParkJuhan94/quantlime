package com.quantlime.infra.oauth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    private final Provider google;
    private final Provider kakao;
    private final Provider naver;

    @Getter
    @RequiredArgsConstructor
    public static class Provider {

        private final String clientId;
        private final String clientSecret;
        private final String tokenUri;
        private final String userInfoUri;
    }
}
