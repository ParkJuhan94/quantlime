package com.quantlime.watchlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderWatchlistGroupsRequest(
    @Schema(description = "원하는 순서대로 나열한 그룹 ID 목록", example = "[3, 1, 2]")
    @NotEmpty(message = "순서를 변경할 그룹 목록은 필수입니다.") List<Long> groupIds
) {
}
