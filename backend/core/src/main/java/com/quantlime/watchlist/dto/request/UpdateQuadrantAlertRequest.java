package com.quantlime.watchlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateQuadrantAlertRequest(
    @Schema(description = "스코어 사분면 변경 알림 사용 여부", example = "true")
    @NotNull(message = "알림 사용 여부는 필수입니다.") Boolean enabled
) {
}
