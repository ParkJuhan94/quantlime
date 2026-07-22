package com.quantlime.infra.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleUserInfoResponse(
    String sub,
    String email,
    String name,
    String picture
) {
}
