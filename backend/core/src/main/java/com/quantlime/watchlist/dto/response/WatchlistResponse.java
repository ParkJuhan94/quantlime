package com.quantlime.watchlist.dto.response;

import java.time.LocalDateTime;
import org.springframework.lang.Nullable;

public record WatchlistResponse(
    Long id,
    String stockCode,
    String stockName,
    String marketType,
    String sector,
    @Nullable Long groupId,
    int sortOrder,
    LocalDateTime createdAt,
    String logoUrl
) {
}
