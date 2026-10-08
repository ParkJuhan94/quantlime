package com.quantlime.watchlist.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateQuadrantAlertRequest(
    @NotNull(message = "알림 사용 여부는 필수입니다.") Boolean enabled
) {
}
