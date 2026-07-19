package com.quantlab.watchlist.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderWatchlistRequest(
    @NotEmpty(message = "순서를 변경할 관심 종목 목록은 필수입니다.") List<Long> watchlistIds
) {
}
