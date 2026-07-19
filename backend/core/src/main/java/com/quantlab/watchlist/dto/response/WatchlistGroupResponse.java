package com.quantlab.watchlist.dto.response;

public record WatchlistGroupResponse(
    Long id,
    String name,
    int sortOrder
) {
}
