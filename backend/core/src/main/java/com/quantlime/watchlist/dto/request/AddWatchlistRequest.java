package com.quantlime.watchlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 등록 시점에 그룹을 함께 지정한다("미분류" 폐지 - 관심 종목은 등록과
// 동시에 반드시 어느 한 그룹에 속해야 한다).
public record AddWatchlistRequest(
    @Schema(description = "등록할 관심종목 그룹 ID", example = "1")
    @NotNull(message = "그룹은 필수입니다.") Long groupId
) {
}
