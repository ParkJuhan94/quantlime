package com.quantlab.watchlist;

import com.quantlab.user.domain.User;
import com.quantlab.watchlist.domain.WatchlistGroup;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class WatchlistGroupFixture {

    public static WatchlistGroup createWatchlistGroup(User user) {
        return WatchlistGroup.of(user, "테스트그룹", 0);
    }
}
