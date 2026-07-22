package com.quantlime.infra.tradingview.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET {baseUrl}/symbol?symbol={symbol}&fields=close,change 응답(실제 호출로
 * 확인). 채권 금리는 관례상 포인트 변화(%p)로 표기하지만, 다른 지수 카드와
 * 시각적 일관성을 우선해달라는 요청(2026-07-17)에 따라 상대 등락률(change,
 * %)을 그대로 쓴다 - TradingView 응답이 이미 계산해서 내려주므로 별도
 * 변환 로직이 필요 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TradingViewSymbolResponse(
    Double close,
    @JsonProperty("change") Double changeRate
) {
}
