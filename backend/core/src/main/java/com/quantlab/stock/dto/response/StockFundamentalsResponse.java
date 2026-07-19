package com.quantlab.stock.dto.response;

/**
 * 시총/PER/PBR/PSR/부채비율 등 밸류에이션 지표. 네이버 금융 비공식 API
 * 조회 특성상 개별 필드가 없거나 파싱 실패할 수 있어 전부 nullable이다
 * (프론트는 값이 없는 항목만 조용히 숨긴다).
 */
public record StockFundamentalsResponse(
    Double marketCap,
    Double per,
    Double forwardPer,
    Double pbr,
    Double psr,
    Double debtRatio
) {
}
