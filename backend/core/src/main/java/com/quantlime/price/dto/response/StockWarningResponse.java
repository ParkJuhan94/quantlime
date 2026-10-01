package com.quantlime.price.dto.response;

/** warningType은 토스 코드 원문, label은 화면 표시용 한글(알 수 없는 코드는 원문 그대로). */
public record StockWarningResponse(
    String warningType,
    String label,
    String exchange,
    String startDate,
    String endDate
) {
}
