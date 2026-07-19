package com.quantlab.watchlist.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderWatchlistGroupsRequest(
    @NotEmpty(message = "순서를 변경할 그룹 목록은 필수입니다.") List<Long> groupIds
) {
}
