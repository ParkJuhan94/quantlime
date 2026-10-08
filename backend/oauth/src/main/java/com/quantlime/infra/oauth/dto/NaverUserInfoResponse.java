package com.quantlime.infra.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverUserInfoResponse(String resultcode, String message, NaverAccount response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NaverAccount(
        String id,
        String email,
        String nickname,
        @JsonProperty("profile_image") String profileImage
    ) {
    }
}
