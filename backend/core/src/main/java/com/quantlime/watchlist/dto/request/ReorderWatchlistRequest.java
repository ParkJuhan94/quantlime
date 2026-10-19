package com.quantlime.watchlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderWatchlistRequest(
    @Schema(description = "원하는 순서대로 나열한 관심종목 ID 목록", example = "[10, 12, 11]")
    @NotEmpty(message = "순서를 변경할 관심 종목 목록은 필수입니다.") List<Long> watchlistIds
) {
}
