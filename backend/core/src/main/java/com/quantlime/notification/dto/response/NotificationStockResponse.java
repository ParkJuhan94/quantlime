package com.quantlime.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationStockResponse(
    @Schema(description = "종목 코드. 종목 상세 경로(/stocks/{code})에 그대로 쓴다", example = "005930")
    String code,
    @Schema(description = "알림을 만들 때의 종목명", example = "삼성전자")
    String name
) {
}
