package com.quantlime.infra.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET {chartBaseUrl}/marketindex/exchange/{pair} 응답(prices와 다른 경로 -
 * 실제 호출로 확인). 숫자 필드들이 최상위가 아니라 exchangeInfo 아래에
 * 중첩돼 내려온다(실제 호출로 재확인 - 처음엔 최상위로 잘못 가정해
 * fluctuationsRatio가 계속 null로 파싱됐었음). 하나은행 고시환율 기준
 * 당일 등락률(fluctuationsRatio)은 전일 마지막 고시가 대비라 사실상
 * 한국시간 자정 전후로 갱신되는 값이다 - 지수 카드의 "오늘 등락률"
 * 표시에 그대로 쓴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverExchangeRateBasicResponse(ExchangeInfo exchangeInfo) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExchangeInfo(
        String closePrice,
        String fluctuations,
        String fluctuationsRatio,
        String marketStatus,
        String localTradedAt
    ) {
    }
}
