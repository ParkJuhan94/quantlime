package com.quantlab.watchlist;

import com.quantlab.stock.domain.Stock;
import com.quantlab.user.domain.User;
import com.quantlab.watchlist.domain.Watchlist;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class WatchlistFixture {

    public static Watchlist createWatchlist(User user, Stock stock) {
        return Watchlist.of(user, stock);
    }
}
