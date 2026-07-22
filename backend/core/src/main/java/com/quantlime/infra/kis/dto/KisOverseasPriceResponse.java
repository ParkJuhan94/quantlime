package com.quantlime.infra.kis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /uapi/overseas-price/v1/quotations/price (tr_id=HHDFS00000300) 응답.
 * 2026-07-16 실제 앱키로 라이브 호출(NAS/AAPL)해 rt_cd=0 정상 응답과
 * 아래 필드 전부(last/diff/rate/sign/tvol) 확인 완료.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisOverseasPriceResponse(
    @JsonProperty("rt_cd") String rtCd,
    String msg1,
    Output output
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(
        String last,
        String diff,
        String rate,
        String sign,
        String tvol
    ) {
    }
}
