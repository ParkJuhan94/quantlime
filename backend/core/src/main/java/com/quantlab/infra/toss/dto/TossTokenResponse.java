package com.quantlab.infra.toss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TossTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn
) {
}
