package com.quantlime.price.dto;

import java.time.LocalDate;

/**
 * {@link com.quantlime.price.util.PriceJumpDetector}가 찾아낸, 정규장
 * 가격제한폭으로는 설명되지 않는 종가 변동 1건(수정주가 소급 재조정 의심).
 */
public record PriceJumpReport(
    String stockCode,
    LocalDate tradeDate,
    LocalDate previousTradeDate,
    double previousClose,
    double close,
    double ratio
) {
}
