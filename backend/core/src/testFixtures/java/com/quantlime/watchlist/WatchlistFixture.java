package com.quantlime.watchlist;

import com.quantlime.stock.domain.Stock;
import com.quantlime.user.domain.User;
import com.quantlime.watchlist.domain.Watchlist;
import com.quantlime.watchlist.domain.WatchlistGroup;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class WatchlistFixture {

    public static Watchlist createWatchlist(User user, Stock stock, WatchlistGroup group) {
        return Watchlist.of(user, stock, group, 0);
    }
}
