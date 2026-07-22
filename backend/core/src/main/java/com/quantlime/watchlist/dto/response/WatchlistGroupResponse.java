package com.quantlime.watchlist.dto.response;

public record WatchlistGroupResponse(
    Long id,
    String name,
    int sortOrder
) {
}
