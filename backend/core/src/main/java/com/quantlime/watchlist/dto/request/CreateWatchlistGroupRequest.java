package com.quantlime.watchlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateWatchlistGroupRequest(
    @Schema(description = "새 그룹 이름", example = "반도체")
    @NotBlank(message = "그룹 이름은 필수입니다.") String name
) {
}
