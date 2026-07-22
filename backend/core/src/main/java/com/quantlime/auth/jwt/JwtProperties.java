package com.quantlime.auth.jwt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private final String secret;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;
}
