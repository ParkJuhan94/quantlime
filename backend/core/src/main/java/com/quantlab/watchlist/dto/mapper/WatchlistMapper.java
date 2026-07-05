package com.quantlab.watchlist.dto.mapper;

import com.quantlab.watchlist.domain.Watchlist;
import com.quantlab.watchlist.dto.response.WatchlistResponse;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class WatchlistMapper {

    public static WatchlistResponse toWatchlistResponse(Watchlist watchlist) {
        return new WatchlistResponse(
            watchlist.getId(),
            watchlist.getStock().getStockCode(),
            watchlist.getStock().getStockName(),
            watchlist.getStock().getMarketType().getLabel(),
            watchlist.getStock().getSector(),
            watchlist.getCreatedAt()
        );
    }
}
