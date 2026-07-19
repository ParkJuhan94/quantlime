package com.quantlab.watchlist.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateWatchlistGroupRequest(
    @NotBlank(message = "그룹 이름은 필수입니다.") String name
) {
}
