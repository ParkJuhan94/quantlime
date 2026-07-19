package com.quantlab.infra.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET /api/index/{code}/basic 응답. 숫자 필드가 전부 콤마 포맷 문자열로
 * 내려온다("7,284.41") - {@link com.quantlab.infra.naver.NaverFinanceApiClient}가
 * 아니라 매퍼 쪽에서 콤마를 제거해 파싱한다(원시값 보존 목적).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverIndexBasicResponse(
    String stockName,
    String closePrice,
    String compareToPreviousClosePrice,
    String fluctuationsRatio,
    String marketStatus,
    String localTradedAt,
    OverMarketPriceInfo overMarketPriceInfo
) {

    // 미국 시장(나스닥/S&P500/SOXX)만 내려주는 필드(실제 호출로 확인 -
    // 국내 지수 응답엔 이 필드 자체가 없어 null). 정규장 마감 중에도
    // 프리마켓/애프터마켓이 열려 있으면 그 시점의 시세를 별도로 준다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverMarketPriceInfo(
        String tradingSessionType,
        String overMarketStatus,
        String overPrice,
        String fluctuationsRatio
    ) {
    }
}
