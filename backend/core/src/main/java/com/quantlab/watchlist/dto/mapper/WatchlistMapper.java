package com.quantlab.watchlist.dto.mapper;

import com.quantlab.watchlist.domain.Watchlist;
import com.quantlab.watchlist.domain.WatchlistGroup;
import com.quantlab.watchlist.dto.response.WatchlistGroupResponse;
import com.quantlab.watchlist.dto.response.WatchlistResponse;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class WatchlistMapper {

    public static WatchlistResponse toWatchlistResponse(Watchlist watchlist) {
        WatchlistGroup group = watchlist.getGroup();
        return new WatchlistResponse(
            watchlist.getId(),
            watchlist.getStock().getStockCode(),
            watchlist.getStock().getStockName(),
            watchlist.getStock().getMarketType().getLabel(),
            watchlist.getStock().getSector(),
            group != null ? group.getId() : null,
            watchlist.getSortOrder(),
            watchlist.getCreatedAt()
        );
    }

    public static WatchlistGroupResponse toWatchlistGroupResponse(WatchlistGroup group) {
        return new WatchlistGroupResponse(group.getId(), group.getName(), group.getSortOrder());
    }
}
