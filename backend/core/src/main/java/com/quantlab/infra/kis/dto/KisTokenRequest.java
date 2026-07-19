package com.quantlab.infra.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /oauth2/tokenP 요청 바디. 토스와 달리 KIS는 form-urlencoded가
 * 아니라 JSON 바디로 발급을 요청한다(실제 API 차이).
 */
public record KisTokenRequest(
    @JsonProperty("grant_type") String grantType,
    String appkey,
    String appsecret
) {

    public static KisTokenRequest of(String appKey, String appSecret) {
        return new KisTokenRequest("client_credentials", appKey, appSecret);
    }
}
