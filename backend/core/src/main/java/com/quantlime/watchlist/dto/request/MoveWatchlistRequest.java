package com.quantlime.watchlist.dto.request;

import jakarta.validation.constraints.NotNull;

// "미분류" 폐지 이후로는 groupId가 항상 필수다 - 관심 종목은 반드시
// 어느 한 그룹에 속해야 한다.
public record MoveWatchlistRequest(
    @NotNull(message = "그룹은 필수입니다.") Long groupId
) {
}
