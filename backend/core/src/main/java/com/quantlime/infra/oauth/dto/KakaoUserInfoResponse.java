package com.quantlime.infra.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserInfoResponse(
    Long id,
    @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(String email, KakaoProfile profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoProfile(
        String nickname,
        @JsonProperty("profile_image_url") String profileImageUrl
    ) {
    }
}
