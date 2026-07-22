package com.quantlime.watchlist.dto.mapper;

import com.quantlime.watchlist.domain.Watchlist;
import com.quantlime.watchlist.domain.WatchlistGroup;
import com.quantlime.watchlist.dto.response.WatchlistGroupResponse;
import com.quantlime.watchlist.dto.response.WatchlistResponse;
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
