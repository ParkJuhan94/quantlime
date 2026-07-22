package com.quantlime.watchlist.dto.response;

import java.time.LocalDateTime;

public record WatchlistResponse(
    Long id,
    String stockCode,
    String stockName,
    String marketType,
    String sector,
    Long groupId,
    int sortOrder,
    LocalDateTime createdAt
) {
}
