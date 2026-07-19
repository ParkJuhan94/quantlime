package com.quantlab.auth.dto.mapper;

import com.quantlab.auth.dto.response.TokenResponse;
import java.util.concurrent.TimeUnit;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class AuthMapper {

    private static final String TOKEN_TYPE = "Bearer";

    public static TokenResponse toTokenResponse(String accessToken, long accessTokenValidityMs) {
        return new TokenResponse(
            accessToken,
            TOKEN_TYPE,
            TimeUnit.MILLISECONDS.toSeconds(accessTokenValidityMs)
        );
    }
}
